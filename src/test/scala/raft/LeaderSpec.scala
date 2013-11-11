package raft

import scala.language.postfixOps
import org.scalatest._
import akka.testkit._
import scala.concurrent.duration._

class LeaderSpec extends RaftSpec {

  val leader = TestFSMRef(new Raft())
  val probes = (for (i <- List.range(0, 3)) yield TestProbe())
  val allNodes = testActor :: leader :: probes.map(_.ref)

  val exitCandidateState = Data(
    currentTerm = 2,
    votedFor = Some(leader), // voted for self
    log = List(LogEntry("a", 1), LogEntry("b", 1)),
    commitIndex = 1,
    lastApplied = 1,

    // just before majority
    votesReceived = List(leader, probes(0).ref),
    nodes = allNodes
  )

  val isLeaderState = Data(
    currentTerm = 2,
    votedFor = None,
    log = List(LogEntry("a", 1), LogEntry("b", 1), LogEntry("c", 2)),
    commitIndex = 2,
    lastApplied = 2,
    votesReceived = List(),
    nodes = allNodes
  )

  "upon election a leader" must {
    "send a heartbeat to each server to establish its authority" in {
      leader.setState(Candidate, exitCandidateState)
      leader ! GrantVote(2) // makes candidate become leader
      Thread.sleep(30)
      val message = AppendEntries(2, leader, 1, 1, List(), 1)
      expectMsg(message) // empty log = heartbeat
      probes.map(x => x.expectMsg(message))
    }

    "initialise a next index for each follower to leader's last log index + 1" in {
      leader.setState(Candidate, exitCandidateState)
      leader ! GrantVote(2) // makes candidate become leader
      Thread.sleep(30)
      leader.stateData.nextIndex must contain key (testActor)
      leader.stateData.nextIndex(testActor) must be(exitCandidateState.log.length)
    }

    "initialise a match index for each follower to 0" in {
      leader.setState(Candidate, exitCandidateState)
      leader ! GrantVote(2) // makes candidate become leader
      Thread.sleep(30)
      leader.stateData.matchIndex must contain key (testActor)
      leader.stateData.matchIndex(testActor) must be(0)
    }

    "have heartbeat timer set" in {
      leader.setState(Candidate, exitCandidateState)
      leader ! GrantVote(2) // makes candidate become leader
      Thread.sleep(10)
      leader.isTimerActive("timeout") must be(true)
    }
  }

  "when receiving a client command a leader" must {
    "append entry to its local log" in {
      leader.setState(Leader, isLeaderState)
      leader ! ClientCommand(100, "add")
      leader.stateData.log must contain(LogEntry("add", 2)) // 2 == currentTerm
    }

    "create a pending client request" in {
      leader.setState(Leader, isLeaderState)
      leader ! ClientCommand(100, "add")
      leader.stateData.pendingRequests must contain key (ClientRef(testActor, 100))
    }

    "respond to client after entry is applied to state machine" in {
      pending
    }

    "broadcast AppendEntries rpc to all followers" in {
      val probes = for (i <- List.range(0, 4)) yield TestProbe()
      val probedState = isLeaderState.copy(nodes = probes.map(_.ref))
      leader.setState(Leader, probedState)
      leader ! ClientCommand(100, "add")
      for (p <- probes) yield p.expectMsg(AppendEntries(
        term = 2,
        leaderId = leader,
        prevLogIndex = 3,
        prevLogTerm = 2,
        entries = List(LogEntry("add", 2)),
        leaderCommit = 2
      ))
    }
  }

  "a leader" must {
    "repeatedly send heartbeats if no other calls are being made" in {
      pending
    }

    "reschedule a heartbeat if an append entries rpc call is made" in {
      leader.setState(Leader, isLeaderState)
      leader.setTimer("timeout", Timeout, 100 millis, false)
      Thread.sleep(80)
      leader ! ClientCommand(100, "add")
      Thread.sleep(80) // 80+50 is enough to cause timeout
      leader.isTimerActive("timeout")
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