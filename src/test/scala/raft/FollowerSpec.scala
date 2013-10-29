package raft

import org.scalatest._
import akka.testkit._

class FollowerSpec extends RaftSpec {
	
  val candidateId: Raft.NodeId = 1
  val follower = TestFSMRef(new Raft())
  
  "a follower" must {
    "reply to AppendEntriesRPC" in {
      // check notes in paper before continuing 
    }
    
    "grant vote if sender term is higher to own term" in {
      follower.setState(Follower, Data(
          currentTerm = 2,
          votedFor = None,
          log = List(),
          commitIndex = 1,
          lastApplied = 1
          ))
      follower ! RequestVote(3, candidateId, 2, 2)
      expectMsg(GrantVote(2)) // should follower update term?
    }
    
    "deny vote if own log's last term is more up to date than candidate" in {
//      If the logs have last entries with different terms
//      then the log with the later term is more up to date. 
//      If the logs end with the same term, then whichever log is 
//      longer (i.e., logIndex) is more up to date.
      follower.setState(Follower, Data(
          currentTerm = 2,
          votedFor = None,
          log = List(LogEntry("a", 2), LogEntry("b", 2)),
          commitIndex = 1,
          lastApplied = 1
          ))
      follower ! RequestVote(
          term = 2,
          candidateId = candidateId,
          lastLogIndex = 2,
          lastLogTerm = 1
          )
      expectMsg(DenyVote(2))
    }
    
    "deny vote if own log's last term is equal but log is longer than candidate" in {
//      If the logs have last entries with different terms
//      then the log with the later term is more up to date. 
//      If the logs end with the same term, then whichever log is 
//      longer (i.e., logIndex) is more up to date.
      follower.setState(Follower, Data(
          currentTerm = 2,
          votedFor = None,
          log = List(LogEntry("a", 2), LogEntry("b", 2), LogEntry("c", 2)),
          commitIndex = 1,
          lastApplied = 1
          ))
      follower ! RequestVote(
          term = 2,
          candidateId = candidateId,
          lastLogIndex = 1, // shorter log than follower
          lastLogTerm = 2
          )
      expectMsg(DenyVote(2))
    }
    
    "deny vote if term is lower than own term" in {
      follower.setState(Follower, Data(
          currentTerm = 3,
          votedFor = None,
          log = List(),
          commitIndex = 1,
          lastApplied = 1
          ))
      follower ! RequestVote(2, candidateId, 2, 2)
      expectMsg(DenyVote(3)) 
    }
    
    "deny vote if vote for term already cast" in {
      follower.setState(Follower, Data(
          currentTerm = 2,
          votedFor = Some(2),
          log = List(),
          commitIndex = 1,
          lastApplied = 1
          ))
      follower ! RequestVote(3, candidateId, 2, 2)
      expectMsg(DenyVote(2))
    }
    
    "convert to candidate if no AppendEntriesRPCs are received from the leader within timeout" in {
      
    }
    
    "convert to candidate if no GrantVoteRPC is received within timeout" in {
      
    } 
    
    "reset timeout after receiving AppendEntriesRPC" in {
      
    }
    
    "reset timeout after receiving GrantVoteRPC" in {
      
    }
    
    "increase its term when transitioning to candidate" in {
      
    }
  }
}