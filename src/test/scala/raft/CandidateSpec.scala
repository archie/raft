package raft

import scala.language.postfixOps
import org.scalatest._
import akka.testkit._
import scala.concurrent.duration._

class CandidateSpec extends RaftSpec {

  val candidate = TestFSMRef(new Raft())
   
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
      pending
    }
    
    "convert to follower if receiving append entries message from new leader" in {
      pending
    }
    
    "start a new election if timeout elapses" in {
      pending
    }
  }
}