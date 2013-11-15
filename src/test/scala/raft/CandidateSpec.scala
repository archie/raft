package raft

import scala.language.postfixOps
import org.scalatest._
import akka.testkit._
import akka.actor.ActorDSL._
import scala.concurrent.duration._

class CandidateSpec extends RaftSpec {

  val candidate = TestFSMRef(new Raft())

  val probes = (for (i <- List.range(0, 3)) yield TestProbe())
  val allNodes = testActor :: candidate :: probes.map(_.ref)

  val totalOrdering = new TotalOrdering

  val initialCandidateState = Meta(
    term = Term(3),
    log = Log(allNodes, List(LogEntry("a", 1), LogEntry("b", 2))),
    rsm = totalOrdering,
    nodes = allNodes
  )

  "when converting to a candidate it" must {
    "increase its term" in {
      candidate.setState(Follower, initialCandidateState) // reusing state
      candidate.setTimer("timeout", Timeout, 1 millis, false) // force transition
      Thread.sleep(40) // ensure timeout elapses
      candidate.stateData.term.current must be(4)
    }

    "vote for itself" in {
      candidate.setState(Follower, initialCandidateState) // reusing state
      candidate.setTimer("timeout", Timeout, 1 millis, false) // force transition
      Thread.sleep(40) // ensure timeout elapses
      candidate.stateData.votes.received must contain(candidate)
    }

    "reset election timeout" in {
      candidate.setState(Follower, initialCandidateState) // reusing state
      candidate.setTimer("force transition", Timeout, 1 millis, false)
      Thread.sleep(40) // ensure timeout elapses
      candidate.isTimerActive("timeout") must be(true) // check that default timer is set
    }

    "request votes from all other servers" in {
      candidate.setState(Follower, initialCandidateState) // reusing state
      candidate.setTimer("timeout", Timeout, 1 millis, false) // force transition
      Thread.sleep(40) // ensure timeout elapses
      expectMsg(RequestVote(4, candidate, 0, 2))
      expectMsg(RequestVote(4, candidate, 0, 2))
      expectMsg(RequestVote(4, candidate, 0, 2))
      expectMsg(RequestVote(4, candidate, 0, 2))
    }
  }

  "a candidate" must {
    "become leader if receiving grants from a majority of servers" in {
      val cand = TestFSMRef(new Raft())
      cand.setState(Candidate, initialCandidateState)

      for (i <- List.range(0, 4)) // excluding candidate  
        yield actor(new Act {
        whenStarting { cand ! GrantVote(3) }
      })

      Thread.sleep(50) // give candidate time to receive and parse messages
      cand.stateName must be(Leader)
    }

    "remain a candidate if the majority vote is not yet received" in {
      val cand = TestFSMRef(new Raft())
      cand.setState(Candidate, initialCandidateState)
      for (i <- List.range(0, 2)) // yields two votes  
        yield actor(new Act {
        whenStarting { cand ! GrantVote(3) }
      })
      Thread.sleep(50)
      cand.stateName must be(Candidate)
    }

    "ensure that receiving grant does not trigger new request vote" in {
      val probe = TestProbe()
      val cand = TestFSMRef(new Raft())
      cand.setState(Candidate, initialCandidateState)
      probe.send(cand, GrantVote(3))
      probe.expectNoMsg
    }

    "convert to follower if receiving append entries message from new leader" in {
      candidate.setState(Candidate, initialCandidateState)
      candidate ! AppendEntries(
        term = 4,
        leaderId = testActor,
        prevLogIndex = 3,
        prevLogTerm = 2,
        entries = List(LogEntry("op", 2)),
        leaderCommit = 0
      )
      candidate.stateName must be(Follower)
    }

    "start a new election if timeout elapses" in {
      candidate.setState(Candidate, initialCandidateState) // reusing state
      candidate.setTimer("timeout", Timeout, 1 millis, false) // force transition
      Thread.sleep(40) // ensure timeout elapses

      // i.e., no messages are received within the timeout 
      candidate.stateName must be(Candidate)
      candidate.stateData.term.current must be(initialCandidateState.term.current + 1)
    }
  }
}