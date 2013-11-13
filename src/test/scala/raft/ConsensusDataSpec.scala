package raft

import org.scalatest._

class ConsensusDataSpec extends WordSpecLike
    with MustMatchers with BeforeAndAfterEach {

  "term" must {
    "increase term monotonically" in (pending)
    "has read only access to current term" in (pending) 
  }
  
  "votes" must {
    "keep at most one vote for a candidate per term" in (pending)
    "keep track of votes received" in (pending)
  }
  
  "client requests" must {
    "store pending requests" in (pending)
    "increase append success count per request" in (pending)
    "check if majority has been reached per request" in (pending)
    "delete requests that have been replied to" in (pending)
  }
  
  "log" must {
    "maintain a next index for each follower" in (pending)
    "decrement the next index for a follower if older log entries must be passed" in (pending)
    "set the next index for a follower based on the last log entry sent" in (pending)
  }
  
  "replicated state machine" must {
    "apply commands to a generic state machine" in (pending)
    "keep track of the log index of the last command applied to the state machine" in (pending)
  }
  
  "state factory" must {
    "create a state object from file" in (pending)
    "persist a state object to file" in (pending)
  }
}