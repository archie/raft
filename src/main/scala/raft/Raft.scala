package raft 

import akka.actor.{ Actor, ActorRef, FSM }
import scala.concurrent.duration._

/* types */
object Raft {
  type Term = Int
  type NodeId = Int
}

case class LogEntry(entry: String, term: Raft.Term)

/* messages */
sealed trait Message
case class RequestVote(term: Raft.Term, candidateId: Raft.NodeId,
    lastLogIndex: Int, lastLogTerm: Raft.Term)
case class DenyVote(term: Raft.Term) extends Message
case class GrantVote(term: Raft.Term) extends Message

/* states */
sealed trait Role
case object Leader extends Role 
case object Follower extends Role
case object Candidate extends Role

/* state data */
case class Data(currentTerm: Raft.Term, votedFor: Option[Raft.NodeId],
    log: List[LogEntry], commitIndex: Int, lastApplied: Int)

/* Consensus module */
class Raft() extends Actor with FSM[Role, Data] {
  startWith(Follower, Data(0, None, List(), 0, 0))
  
  when(Follower) {
    case Event(rpc: RequestVote, data: Data) =>
      val (castVote, newStateData) = vote(rpc, data)
      stay using newStateData replying castVote
  }
  
  whenUnhandled {
    case Event(s, d) =>
      stay
  }
  
  initialize()
  
  /*
   * Determine whether to grant or deny vote
   */
  private def vote(rpc: RequestVote, data: Data): (Message, Data) =
	  if (alreadyVoted(data)) deny(data)
	  else if (rpc.term < data.currentTerm) deny(data)
	  else if (rpc.term == data.currentTerm)
	    if (data.log.last.term > rpc.lastLogTerm) 
	      deny(data)
	    else if ((data.log.last.term == rpc.lastLogTerm) && 
	        		 (data.log.length-1 > rpc.lastLogIndex)) 
	        deny(data)
	    else grant(rpc, data)
	  else grant(rpc, data)
  
  private def deny(d: Data) = (DenyVote(d.currentTerm), d)
  private def grant(rpc: RequestVote, d: Data): (Message, Data) = {
    val newdata = d.copy(votedFor = Some(rpc.candidateId))
	  (GrantVote(newdata.currentTerm), newdata)
  }
  
  private def alreadyVoted(data: Data): Boolean = data.votedFor match {
    case Some(_) => true
    case None => false
  }
}
