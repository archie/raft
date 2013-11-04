package raft

import scala.language.postfixOps
import org.scalatest._
import akka.testkit._
import akka.actor.ActorDSL._
import scala.concurrent.duration._

class CandidateSpec extends RaftSpec {

  val candidate = TestFSMRef(new Raft())
  val initialCandidateState = Data(
      currentTerm = 3, 
      votedFor = None,
      log = List(),
      commitIndex = 0,
      lastApplied = 0,
      nodes = List(testActor, testActor, testActor, testActor, candidate),
      votesReceived = List())
   
  "when converting to a candidate it" must {
    "increase its term" in {
      candidate.setState(Follower, initialCandidateState) // reusing state
      candidate.setTimer("timeout", Timeout, 1 millis, false) // force transition
      Thread.sleep(40) // ensure timeout elapses
      candidate.stateData.currentTerm must be (4)
    }
    
    "vote for itself" in {
      candidate.setState(Follower, initialCandidateState) // reusing state
      candidate.setTimer("timeout", Timeout, 1 millis, false) // force transition
      Thread.sleep(40) // ensure timeout elapses
      candidate.stateData.votesReceived must contain (candidate)
    }
    
    "reset election timeout" in {
      candidate.setState(Follower, initialCandidateState) // reusing state
      candidate.setTimer("force transition", Timeout, 1 millis, false)
      Thread.sleep(40) // ensure timeout elapses
      candidate.isTimerActive("timeout") must be (true) // check that default timer is set
    }
    
    "request votes from all other servers" in {
      candidate.setState(Follower, initialCandidateState) // reusing state
      candidate.setTimer("timeout", Timeout, 1 millis, false) // force transition
      Thread.sleep(40) // ensure timeout elapses
      expectMsg(RequestVote(4, candidate, 0, 0))
      expectMsg(RequestVote(4, candidate, 0, 0))
      expectMsg(RequestVote(4, candidate, 0, 0))
      expectMsg(RequestVote(4, candidate, 0, 0))
    }
  }
  
  "a candidate" must {
    "become leader if receiving grants from a majority of servers" in {
      candidate.setState(Candidate, initialCandidateState)
      
      // sending grant votes
			for (i <- List.range(0, 4)) // excluding candidate  
			  yield actor(new Act {
			    	whenStarting { candidate ! GrantVote(3) }
			    }) 
      
      Thread.sleep(50) // give candidate time to receive and parse messages
      
      candidate.stateName must be (Leader)
    }
    
    "remain a candidate if the majority vote is not yet received" in {
      candidate.setState(Candidate, initialCandidateState)
      for (i <- List.range(0, 2)) // yields two votes  
			  yield actor(new Act {
			    	whenStarting { candidate ! GrantVote(3) }
			    })
      Thread.sleep(50)
      candidate.stateName must be (Candidate)
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
      candidate.stateName must be (Follower)
    }
    
    "start a new election if timeout elapses" in {
      pending
      // i.e., no messages are received within the timeout
    }
  }
}