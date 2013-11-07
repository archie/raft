package raft

import scala.language.postfixOps
import org.scalatest._
import akka.testkit._
import scala.concurrent.duration._

class LeaderSpec extends RaftSpec {
  
  val leader = TestFSMRef(new Raft())
  
  val exitCandidateState = Data(
      currentTerm = 2,
      votedFor = None, // voted for self
      log = List(LogEntry("a", 1), LogEntry("b", 1)),
      commitIndex = 1,
      lastApplied = 1
      )
  
  "upon election a leader" must {
    "send a heartbeat to each server to establish its authority" in {
      pending
    }
    
    "initialise a next index for each follower to leader's last log index + 1" in {
      pending
    }
    
    "initialise a match index for each follower to 0" in {
      pending
    }
  }
  
  "when receiving a client command a leader" must {
  	"append entry to its local log" in {
  		pending
  	}
  	
  	"respond to client after entry is applied to state machine" in {
  		pending
  	}
  }

  "a leader" must {
    "repeatedly send heartbeats if no other calls are being made" in {
      pending
    }
    
    "reschedule a heartbeat if an append entries rpc call is made" in {
    	pending
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