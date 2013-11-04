package raft 

import scala.language.postfixOps
import akka.actor.{ Actor, ActorRef, FSM }
import scala.concurrent.duration._

/* types */
object Raft {
  type Term = Int
  type NodeId = ActorRef
}

case class LogEntry(entry: String, term: Raft.Term)

/* messages */
sealed trait Message
case object Timeout extends Message

sealed trait Request extends Message
case class RequestVote(term: Raft.Term, candidateId: Raft.NodeId,
    lastLogIndex: Int, lastLogTerm: Raft.Term) extends Request
case class AppendEntries(term: Raft.Term, leaderId: Raft.NodeId, 
    prevLogIndex: Int, prevLogTerm: Raft.Term, entries: List[LogEntry],
    leaderCommit: Int) extends Request


sealed trait Vote
case class DenyVote(term: Raft.Term) extends Vote
case class GrantVote(term: Raft.Term) extends Vote

sealed trait AppendReply
case class AppendFailure(term: Raft.Term) extends AppendReply
case class AppendSuccess(term: Raft.Term) extends AppendReply

/* states */
sealed trait Role
case object Leader extends Role 
case object Follower extends Role
case object Candidate extends Role

/* state data */
case class Data(currentTerm: Raft.Term, votedFor: Option[Raft.NodeId],
    log: List[LogEntry], commitIndex: Int, lastApplied: Int,
    // the following entries are state meta data
    nodes: List[ActorRef] = List(), votesReceived: List[ActorRef] = List())

/* Consensus module */
class Raft() extends Actor with FSM[Role, Data] {
  startWith(Follower, Data(0, None, List(), 0, 0)) // TODO: move creation to function
  
  when(Follower) {
    case Event(rpc: RequestVote, data: Data) =>
      vote(rpc, data) match {
        case (msg: GrantVote, updData) =>
          resetTimer
          stay using(updData) replying(msg) 
        case (msg: DenyVote, updData) =>
        	stay using(updData) replying(msg)
      }
    case Event(rpc: AppendEntries, data: Data) =>
      resetTimer
      val (msg, upd) = append(rpc, data)
      stay using upd replying msg
    case Event(Timeout, data) =>
      goto(Candidate) using nextTerm(data)
  }
  
  when(Candidate) {
    case Event(rpc: RequestVote, d: Data) if rpc.candidateId == self =>
      self ! GrantVote(d.currentTerm) 
      stay using d
  	case Event(GrantVote(term), d: Data) if hasMajorityVotes(d) =>
      goto(Leader) using initialLeaderData(d)
    case Event(GrantVote(term), d: Data) =>
      stay using d.copy(votesReceived = sender :: d.votesReceived)
    case Event(rpc: AppendEntries, d: Data) => 
      goto(Follower) using d
  }
  
  when(Leader) {
    case Event(_, _) => 
      stay
  }
  
  whenUnhandled {
    case Event(s, d) =>
      stay
  }
  
  onTransition {
    case Follower -> Candidate =>
      val lastTerm = if (stateData.log.length > 0) stateData.log.last.term else 0
      val lastIndex = if (stateData.log.length > 0) stateData.log.length - 1 else 0
      val nextTerm = stateData.currentTerm + 1
      stateData.nodes.map(t => t ! RequestVote(
          term = nextTerm,
          candidateId = self,
          lastLogIndex = lastIndex,
          lastLogTerm = lastTerm
          ))
      resetTimer
  }
  
  initialize() // akka specific
  
  /*
   *  --- Internals ---
   */
  
  private def initialLeaderData(d: Data) = d.copy()
  
  private def hasMajorityVotes(d: Data) = 
    ((d.votesReceived.length + 1) >= Math.ceil(d.nodes.length / 2.0)) 
    // adds 1 since just received vote is not included
    
  private def resetTimer = {
    cancelTimer("timeout")
    setTimer("timeout", Timeout, 200 millis, false) // should pick random time
  }
    
  private def nextTerm(data: Data): Data = 
    data.copy(currentTerm = data.currentTerm + 1)
    
  private def maxTerm(data: Data, term: Raft.Term): Data = {
    data.copy(currentTerm = Math.max(data.currentTerm, term))
  }
  
  /*
   * AppendEntries handling 
   */
  private def append(rpc: AppendEntries, data: Data): (AppendReply, Data) = { 
    if (leaderIsBehind(rpc, data)) appendFail(data)
    else if (!hasMatchingLogEntryAtPrevPosition(rpc, data)) appendFail(data)
    else appendSuccess(rpc, data)
  }
  
  private def leaderIsBehind(rpc: AppendEntries, data: Data): Boolean = 
    rpc.term < data.currentTerm
    
  private def hasMatchingLogEntryAtPrevPosition(
      rpc: AppendEntries, data: Data): Boolean =
    (data.log.isDefinedAt(rpc.prevLogIndex) && 
    (data.log(rpc.prevLogIndex).term == rpc.prevLogTerm))
  
  private def appendFail(data: Data) =
    (AppendFailure(data.currentTerm), data)
   
  private def appendSuccess(rpc: AppendEntries, data: Data) = {
    // if newer entries exist in log these are not committed and can 
    // safely be removed - should add check during exhaustive testing
    // to ensure property holds
    // TODO: wrap in Log data structure
    val log = data.log.take(rpc.prevLogIndex) ::: rpc.entries
    val updatedData = data.copy(log = log)
    (AppendSuccess(data.currentTerm), updatedData)
  }
  
  /*
   * Determine whether to grant or deny vote
   */
  private def vote(rpc: RequestVote, data: Data): (Vote, Data) =
	  if (alreadyVoted(data)) deny(data)
	  else if (rpc.term < data.currentTerm) deny(data)
	  else if (rpc.term == data.currentTerm)
	    if (candidateLogTermIsBehind(rpc, data)) deny(data)
	    else if (candidateLogTermIsEqualButHasShorterLog(rpc, data)) deny(data)
	    else grant(rpc, data) // follower and candidate are equal, grant
	  else grant(rpc, data) // candidate is ahead, grant
  
  private def deny(d: Data) = (DenyVote(d.currentTerm), d)
  private def grant(rpc: RequestVote, d: Data): (Vote, Data) = {
    val newdata = d.copy(votedFor = Some(rpc.candidateId))
	  (GrantVote(newdata.currentTerm), newdata)
  }
  
  private def candidateLogTermIsBehind(rpc: RequestVote, data: Data) = 
    data.log.last.term > rpc.lastLogTerm
  
  private def candidateLogTermIsEqualButHasShorterLog(rpc: RequestVote, data: Data) =
    (data.log.last.term == rpc.lastLogTerm) && 
	  (data.log.length-1 > rpc.lastLogIndex)
  
  private def alreadyVoted(data: Data): Boolean = data.votedFor match {
    case Some(_) => true
    case None => false
  }
}
