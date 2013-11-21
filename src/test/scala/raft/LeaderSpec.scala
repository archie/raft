package raft

import scala.language.postfixOps
import org.scalatest._
import akka.testkit._
import scala.concurrent.duration._

class LeaderSpec extends RaftSpec with BeforeAndAfterEach {

  val totalOrdering = new TotalOrdering
  def probeGen(size: Int) = (for (i <- List.range(0, size)) yield TestProbe())
  val probes = probeGen(4)
  var leader: TestFSMRef[Role, Meta, Raft] = _
  var exitCandidateState: Meta = _
  var stableLeaderState: Meta = _

  override def beforeEach = {
    leader = TestFSMRef(new Raft())
    val allNodes = leader :: probes.map(_.ref)

    exitCandidateState = Meta(
      term = Term(2),
      log = Log(allNodes, List(LogEntry("a", 1), LogEntry("b", 2))),
      rsm = totalOrdering,
      nodes = allNodes,
      votes = Votes(received = List(leader, allNodes(0))) // just before majority
    )

    stableLeaderState = Meta(
      term = Term(2),
      log = Log(allNodes, List(LogEntry("a", 1), LogEntry("b", 2), LogEntry("c", 2))),
      rsm = totalOrdering,
      nodes = allNodes
    )
  }

  "upon election a leader" must {
    "send a heartbeat to each server to establish its authority" in {
      leader.setState(Candidate, exitCandidateState)
      probes(0).send(leader, GrantVote(2)) // makes candidate become leader
      Thread.sleep(30)
      val message = AppendEntries(2, leader, 1, 2, List(), 0)
      probes.map(x => x.expectMsg(message))
    }

    "send heartbeat even if there are no previous entries in log" in {
      val probes = probeGen(4)
      val nodes = leader :: probes.map(_.ref)
      val state = Meta(
        term = Term(2),
        log = Log(nodes, List()),
        rsm = totalOrdering,
        nodes = nodes,
        votes = Votes(received = List(leader, nodes(2))) // just before majority
      )
      leader.setState(Candidate, state)
      probes(0).send(leader, GrantVote(2)) // makes candidate become leader
      Thread.sleep(30)
      val message = AppendEntries(2, leader, 0, 1, List(), 0)
      probes.map(x => x.expectMsg(message))
    }

    "have heartbeat timer set" in {
      leader.setState(Candidate, exitCandidateState)
      leader ! GrantVote(2) // makes candidate become leader
      Thread.sleep(20)
      leader.isTimerActive("heartbeat") must be(true)
    }
  }

  "when receiving a client command a leader" must {
    "append entry to its local log" in {
      leader.setState(Leader, stableLeaderState)
      probes(0).send(leader, ClientRequest(100, "add"))
      leader.stateData.log.entries must contain(LogEntry("add", 2,
        Some(ClientRef(probes(0).ref, 100)))) // 2 == currentTerm
    }

    "respond to client after entry is applied to state machine" in {
      pending
    }

    "broadcast AppendEntries rpc to all followers" in {
      val nodes = probeGen(4)
      val state = Meta(
        term = Term(2),
        log = Log(nodes.map(_.ref), List(LogEntry("a", 1), LogEntry("b", 2), LogEntry("c", 2))),
        rsm = totalOrdering,
        nodes = nodes.map(_.ref)
      )
      leader.setState(Leader, state)
      leader ! ClientRequest(100, "add")
      nodes.map(_.expectMsg(AppendEntries(
        term = 2,
        leaderId = leader,
        prevLogIndex = 2,
        prevLogTerm = 2,
        entries = List(LogEntry("add", 2, Some(ClientRef(testActor, 100)))),
        leaderCommit = 0
      )))
    }
  }

  "a leader" must {
    "repeatedly send heartbeats if no other calls are being made" in {
      pending
    }

    "send all missing log entries to follower" in {
      // 3-sized cluster
      val probeA = TestProbe()
      val probeB = TestProbe()

      // set state
      val entries = List(LogEntry("a", 1), LogEntry("b", 2), LogEntry("c", 2))
      val nextIndices = Map[Raft.NodeId, Int](
        probeA.ref -> 2, // 2 means probeA is 1 entry behind
        probeB.ref -> 3
      )
      val matchIndices = Map[Raft.NodeId, Int](probeA.ref -> 0, probeB.ref -> 0)
      stableLeaderState.nodes = List(probeA.ref, probeB.ref)
      stableLeaderState.log = Log(entries, nextIndices, matchIndices, 0)
      leader.setState(Leader, stableLeaderState)

      // trigger change
      leader ! ClientRequest(100, "monkey")

      val ref = Some(ClientRef(testActor, 100))
      // test
      probeA.expectMsg(AppendEntries(
        term = 2,
        leaderId = leader,
        prevLogIndex = 1, // verify
        prevLogTerm = 2,
        entries = List(LogEntry("c", 2), LogEntry("monkey", 2, ref)),
        leaderCommit = 0
      ))
      probeB.expectMsg(AppendEntries(
        term = 2,
        leaderId = leader,
        prevLogIndex = 2,
        prevLogTerm = 2,
        entries = List(LogEntry("monkey", 2, ref)),
        leaderCommit = 0
      ))
    }

    "increment next log index on append success" in {
      leader.setState(Leader, stableLeaderState)
      probes(0).send(leader, AppendSuccess(2, 2))
      leader.stateData.log.nextIndex(probes(0).ref) must be(3)
    }

    "set match for index for follower to highest index of appended entries" in {
      leader.setState(Leader, stableLeaderState)
      probes(0).send(leader, AppendSuccess(2, 2))
      leader.stateData.log.matchIndex(probes(0).ref) must be(2)
    }

    "decrement next log index for follower if append entries fail" in {
      leader.setState(Leader, stableLeaderState)
      probes(0).send(leader, AppendFailure(2))
      leader.stateData.log.nextIndex(probes(0).ref) must be(2)
    }

    "convert to follower if term is higher in append failure" in {
      leader.setState(Leader, stableLeaderState)
      probes(0).send(leader, AppendFailure(5))
      leader.stateName must be(Follower)
    }

    "send previous entry if append failed" in {
      val nodes = probeGen(4)
      val state = Meta(
        term = Term(2),
        log = Log(nodes.map(_.ref), List(LogEntry("a", 1), LogEntry("b", 2), LogEntry("c", 2))),
        rsm = totalOrdering,
        nodes = nodes.map(_.ref)
      )
      leader.setState(Leader, state)
      nodes(0).send(leader, AppendFailure(2))
      nodes(0).expectMsg(AppendEntries(
        term = 2,
        leaderId = leader,
        prevLogIndex = 1,
        prevLogTerm = 2,
        entries = List(LogEntry("c", 2)),
        leaderCommit = 0
      ))
    }

    "commit entries if majority exists" in {
      /*
	     * if there exists an N such that N > commitIndex, a majority of 
	     * matchIndex[i] >= N, and log[N].term == currentTerm: 
	     *   set commitIndex = N
	     */

      // Goal is to get a commit for N = 2, and we start one step from majority 

      // 5-sized cluster
      val probeA = TestProbe()
      val probeB = TestProbe()
      val probeC = TestProbe()
      val probeD = TestProbe()

      // set state
      val entries = List(LogEntry("a", 1), LogEntry("b", 2), LogEntry("c", 2))
      val nextIndices = Map[Raft.NodeId, Int](
        probeA.ref -> 3,
        probeB.ref -> 3,
        probeC.ref -> 3,
        probeD.ref -> 3,
        leader -> 3
      )
      val matchIndices = Map[Raft.NodeId, Int](
        probeA.ref -> 2,
        probeB.ref -> 2,
        probeC.ref -> 0,
        probeD.ref -> 0,
        // impossible state, but want to make sure move is triggered by probes 
        leader -> 0
      )
      stableLeaderState.nodes = List(probeA.ref, probeB.ref, probeC.ref, probeD.ref, leader)
      stableLeaderState.log = Log(entries, nextIndices, matchIndices, 1)
      leader.setState(Leader, stableLeaderState)

      // trigger change
      probeC.send(leader, AppendSuccess(2, 2))

      // test
      leader.stateData.log.commitIndex must be(2)
    }

    "do not commit entries if no majority exists" in {
      // 5-sized cluster
      val probeA = TestProbe()
      val probeB = TestProbe()
      val probeC = TestProbe()
      val probeD = TestProbe()

      // set state
      val entries = List(LogEntry("a", 1), LogEntry("b", 2), LogEntry("c", 2))
      val nextIndices = Map[Raft.NodeId, Int](
        probeA.ref -> 3,
        probeB.ref -> 3,
        probeC.ref -> 3,
        probeD.ref -> 3,
        leader -> 3
      )
      val matchIndices = Map[Raft.NodeId, Int](
        probeA.ref -> 2,
        probeB.ref -> 0,
        probeC.ref -> 0,
        probeD.ref -> 0,
        // impossible state, but want to make sure move is triggered by probes 
        leader -> 0
      )
      stableLeaderState.nodes = List(probeA.ref, probeB.ref, probeC.ref, probeD.ref, leader)
      stableLeaderState.log = Log(entries, nextIndices, matchIndices, 1)
      leader.setState(Leader, stableLeaderState)

      // trigger change
      probeC.send(leader, AppendSuccess(2, 2))

      // test
      leader.stateData.log.commitIndex must be(1)
    }

    "apply committed entries" in {
      val leader = TestFSMRef(new Raft())
      val state = stableLeaderState.copy()
      state.log = Log(
        entries = List(LogEntry("a", 1), LogEntry("b", 2), LogEntry("c", 2)),
        nextIndex = probes.map(x => (x.ref, 3)).toMap,
        matchIndex = probes.map(x => (x.ref, 2)).toMap,
        commitIndex = 2,
        lastApplied = 0
      )
      leader.setState(Leader, state)

      // trigger
      probes(0).send(leader, AppendSuccess(2, 2))

      // test
      leader.stateData.log.lastApplied must be(2)
    }
  }
}