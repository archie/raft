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
      pending
    }
    
    "vote for itself" in {
      pending
    }
    
    "reset election timeout" in {
      pending
    }
    
    "request votes from all other servers" in {
      pending
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
      pending
    }
    
    "start a new election if timeout elapses" in {
      pending
      // i.e., no messages are received within the timeout
    }
  }
}