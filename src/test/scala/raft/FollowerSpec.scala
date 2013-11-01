package raft

import scala.language.postfixOps
import org.scalatest._
import akka.testkit._
import scala.concurrent.duration._

class FollowerSpec extends RaftSpec {
	
  val candidateId: Raft.NodeId = 1
  val follower = TestFSMRef(new Raft())
  
  "a follower" must {
    "not append entries if requester's term is less than current term" in {
    	follower.setState(Follower, Data(2, None, List(), 1, 1))
    	follower ! AppendEntries(1, 1, 1, 1, List(LogEntry("op", 1)), 1)
    	expectMsg(AppendFailure(2))
    }
    
    "not append entries if log doesn't contain an entry at " + 
    "previous index" in {
      follower.setState(Follower, Data(
          currentTerm = 2,
          votedFor = None,
          log = List(LogEntry("a", 2), LogEntry("b", 2)),
          commitIndex = 1,
          lastApplied = 1
          ))
      follower ! AppendEntries(
          term = 2,
          leaderId = 1,
          prevLogIndex = 3, // this is one step ahead of follower.log.length-1
          prevLogTerm = 2,
          entries = List(LogEntry("op", 2)),
          leaderCommit = 0
      		)
      expectMsg(AppendFailure(2))
    }
    
    "not append entries if log doesn't contain an entry at " + 
    "previous index with matching terms" in {
      follower.setState(Follower, Data(
          currentTerm = 2,
          votedFor = None,
          log = List(LogEntry("a", 2), LogEntry("b", 2)),
          commitIndex = 0,
          lastApplied = 0
          ))
      follower ! AppendEntries(
          term = 3,
          leaderId = 1,
          prevLogIndex = 1, // last position in follower log
          prevLogTerm = 3,
          entries = List(LogEntry("op", 3)),
          leaderCommit = 0
      		)
      expectMsg(AppendFailure(2))
    }
    
    "remove uncommitted entries if appending at a position" +
    "less than log length" in {
    	follower.setState(Follower, Data(
          currentTerm = 2,
          votedFor = None,
          log = List(LogEntry("a", 2), LogEntry("b", 2), LogEntry("remove", 2)),
          commitIndex = 0,
          lastApplied = 0
          ))
      follower ! AppendEntries(
          term = 3,
          leaderId = 1,
          prevLogIndex = 1, // matches follower's entry ("b", 2)
          prevLogTerm = 2, // matches follower's entry's term
          entries = List(LogEntry("op", 3)),
          leaderCommit = 0
      		)
      expectMsg(AppendSuccess(2))
    	follower.stateData.log must not contain (LogEntry("remove", 2))
    }
    
    "append entry if previous log index and term match" in {
      follower.setState(Follower, Data(
          currentTerm = 2,
          votedFor = None,
          log = List(LogEntry("a", 2), LogEntry("b", 2)),
          commitIndex = 0,
          lastApplied = 0
          ))
      follower ! AppendEntries(
          term = 3,
          leaderId = 1,
          prevLogIndex = 1, // matches follower's last entry
          prevLogTerm = 2, // matches follower's last entry's term
          entries = List(LogEntry("op", 3)),
          leaderCommit = 0
      		)
      expectMsg(AppendSuccess(2))
      follower.stateData.log.last.entry must be ("op")
    }
    
    "append multiple entries if previous log index and term match" in {
      follower.setState(Follower, Data(
          currentTerm = 2,
          votedFor = None,
          log = List(LogEntry("a", 2), LogEntry("b", 2)),
          commitIndex = 0,
          lastApplied = 0
          ))
      follower ! AppendEntries(
          term = 3,
          leaderId = 1,
          prevLogIndex = 1, // matches follower's last entry
          prevLogTerm = 2, // matches follower's last entry's term
          entries = List(LogEntry("op", 3), LogEntry("op2", 3)),
          leaderCommit = 0
      		)
      expectMsg(AppendSuccess(2))
      follower.stateData.log must contain (LogEntry("op", 3))
      follower.stateData.log.last must be (LogEntry("op2", 3))
    }
    
    "grant vote if candidate term is higher to own term" in {
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
    
    "grant vote if candidate is exactly equal" in {
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
          lastLogIndex = 1,
          lastLogTerm = 2
          )
      expectMsg(GrantVote(2))
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
    
    "convert to candidate if no messages are received within timeout" in {
      follower.setState(Follower, Data(2, None,
          List(LogEntry("a", 2), LogEntry("b", 2)), 0, 0))
      follower.setTimer("timeout", Timeout, 5 millis, false)
      Thread.sleep(40)
      follower.stateName must be (Candidate)
    }
    
    "reset timer when granting vote" in {
      follower.setState(Follower, Data(2, None, List(), 1, 1))
      follower.setTimer("timeout", Timeout, 200 millis, false)
      Thread.sleep(150)
      follower ! RequestVote(3, candidateId, 2, 2) // follower grants vote
      Thread.sleep(100) // ensures first timeout has expired 
      follower.stateName must be (Follower) // stays as follower
      follower.isTimerActive("timeout") must be (true)
      
    }
    
    "convert to candidate if timeout reached" in {
      follower.setState(Follower, Data(2, None, List(), 1, 1))
      follower.setTimer("timeout", Timeout, 200 millis, false)
      Thread.sleep(150)
      follower ! RequestVote(1, candidateId, 2, 2) // follower denies this
      Thread.sleep(100) // ensures first timeout has expired 
      follower.stateName must be (Candidate) // timeout happened,
      																			 // transition to candidate
    }
    
    "reset timeout after receiving AppendEntriesRPC" in {
      follower.setState(Follower, Data(2, None,
          List(LogEntry("a", 2), LogEntry("b", 2)), 0, 0))
      follower.setTimer("timeout", Timeout, 200 millis, false)
      Thread.sleep(150)
      follower ! AppendEntries(3, 1, 1, 2, List(LogEntry("op", 3)), 0)
      Thread.sleep(100) // ensures first timeout has expired 
      follower.stateName must be (Follower) // stays as follower
      follower.isTimerActive("timeout") must be (true)
    }
    
    "increase its term when transitioning to candidate" in {
      follower.setState(Follower, Data(2, None,
          List(LogEntry("a", 2), LogEntry("b", 2)), 0, 0))
      follower.setTimer("timeout", Timeout, 10 millis, false)
      follower.stateData.currentTerm must be (2)
      Thread.sleep(30)
      follower.stateData.currentTerm must be (3) // is candidate by now
    }
    
    "increase own term if RequestVote contains higher term" in {
      follower.setState(Follower, Data(2, None,
          List(LogEntry("a", 2), LogEntry("b", 2)), 0, 0))
      follower ! RequestVote(3, 1, 1, 1) // higher term
      follower.stateName must be (Follower)
      follower.stateData.currentTerm must be (3)
    }
    
    "increase own term if AppendEntries contains higher term" in {
      follower.setState(Follower, Data(2, None,
          List(LogEntry("a", 2), LogEntry("b", 2)), 0, 0))
      follower ! AppendEntries(3, 1, 1, 2, List(LogEntry("op", 3)), 0) // higher term
      follower.stateName must be (Follower)
      follower.stateData.currentTerm must be (3)
    }
  }
}