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

    "have heartbeat timer set" in {
      leader.setState(Candidate, exitCandidateState)
      leader ! GrantVote(2) // makes candidate become leader
      Thread.sleep(20)
      leader.isTimerActive("timeout") must be(true)
    }
  }

  "when receiving a client command a leader" must {
    "append entry to its local log" in {
      leader.setState(Leader, stableLeaderState)
      probes(0).send(leader, ClientCommand(100, "add"))
      leader.stateData.log.entries must contain(LogEntry("add", 2)) // 2 == currentTerm
    }

    "create a pending client request" in {
      leader.setState(Leader, stableLeaderState)
      probes(0).send(leader, ClientCommand(100, "add"))
      leader.stateData.requests.pending must contain key (ClientRef(probes(0).ref, 100))
    }

    "respond to client after entry is applied to state machine" in {
      pending
    }

    "broadcast AppendEntries rpc to all followers" in {
      val probes = for (i <- List.range(0, 4)) yield TestProbe()
      stableLeaderState.nodes = probes.map(_.ref)
      leader.setState(Leader, stableLeaderState)
      probes(0).send(leader, ClientCommand(100, "add"))
      probes.map(_.expectMsg(AppendEntries(
        term = 2,
        leaderId = leader,
        prevLogIndex = 2,
        prevLogTerm = 2,
        entries = List(LogEntry("add", 2)),
        leaderCommit = 0
      )))
    }
  }

  "a leader" must {
    "repeatedly send heartbeats if no other calls are being made" in {
      pending
    }

    "reschedule a heartbeat if an append entries rpc call is made" in {
      //      leader.setState(Leader, stableLeaderState)
      //      leader.setTimer("timeout", Timeout, 100 millis, false)
      //      Thread.sleep(80)
      //      leader ! ClientCommand(100, "add")
      //      Thread.sleep(80) // 80+50 is enough to cause timeout
      //      leader.isTimerActive("timeout")
      pending
      // TODO: check if this test case is broken
    }

    "send append entries rpc to follower if last log index is " +
      "higher than or equal to follower's next log index" in {
        pending
      }

    "update next log index and match index for follower if successfull" +
      "append entries rpc" in {
        pending
      }

    "decrement next log index for follower if append entries fail" in {
      pending
    }

    "commit entries" in {
      /*
	     * if there exists an N such that N > commitIndex, a majority of 
	     * matchIndex[i] >= N, and log[N].term == currentTerm: 
	     *   set commitIndex = N
	     */
      pending
    }

    "apply committed entries" in {
      pending
    }
  }
}