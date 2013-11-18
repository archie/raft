package raft

import scala.language.postfixOps
import akka.actor.{ Actor, ActorRef, FSM, LoggingFSM }
import scala.concurrent.duration._
import scala.concurrent.Promise

/* types */
object Raft {
  type Term = Int
  type NodeId = ActorRef
}

// this might go elsewhere later
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
case class AppendSuccess(term: Raft.Term, index: Int) extends AppendReply

/* states */
sealed trait Role
case object Leader extends Role
case object Follower extends Role
case object Candidate extends Role

/* Client messages and data */
case class ClientCommand(id: Int, command: String) extends Message
case class ClientRef(client: ActorRef, cid: Int)
case class ClientRequest(command: ClientCommand, successes: Int = 0)

/* Consensus module */
class Raft() extends Actor with LoggingFSM[Role, Meta] {
  override def logDepth = 12
  startWith(Follower, Meta(List())) // TODO: move creation to function

  when(Follower) {
    case Event(rpc: RequestVote, data) =>
      vote(rpc, data) match {
        case (msg: GrantVote, updData) =>
          resetTimer
          stay using (updData) replying (msg)
        case (msg: DenyVote, updData) =>
          stay using (updData) replying (msg)
      }
    case Event(rpc: AppendEntries, data) =>
      resetTimer
      val (msg, upd) = append(rpc, data)
      stay using upd replying msg
    case Event(Timeout, data) =>
      goto(Candidate) using preparedForCandidate(data)
  }

  when(Candidate) {
    // voting events  
    case Event(rpc: RequestVote, data: Meta) if rpc.candidateId == self =>
      val (msg, updData) = grant(rpc, data)
      stay using (updData) replying (msg)
    case Event(GrantVote(term), data: Meta) =>
      data.votes = data.votes.gotVoteFrom(sender)
      if (data.votes.majority(data.nodes.length))
        goto(Leader) using preparedForLeader(data)
      else stay using data

    // other   
    case Event(rpc: AppendEntries, data: Meta) =>
      goto(Follower) using data
    case Event(Timeout, data: Meta) =>
      goto(Candidate) using preparedForCandidate(data)
  }

  when(Leader) {
    case Event(clientRpc: ClientCommand, data: Meta) =>
      writeToLog(clientRpc, data)
      createPendingRequest(sender, clientRpc, data)
      sendEntries(data)
      stay using data
    case Event(rpc: AppendSuccess, data: Meta) =>
      data.log = data.log.resetNextFor(sender)
      data.log = data.log.matchFor(sender, Some(rpc.index))
      stay
    case Event(rpc: AppendFailure, data: Meta) =>
      if (rpc.term <= data.term.current) {
        data.log = data.log.decrementNextFor(sender)
        resendTo(sender, data)
        stay
      } else {
        data.term = Term(rpc.term)
        data.requests = Requests()
        data.votes = Votes()
        goto(Follower) using data
      }
    //      case Event(succs: AppendSuccess, d: Data) =>
    //         set pendingRequests((sender, succs.id)).successes += 1
    //         if majority(pendingRequests((sender, succs.id).successes)
    //         	then result = statem.apply(pendingRequests((sender, succs.id)).command)
    //            and clientRef ! result
    //        stay
  }

  whenUnhandled {
    case Event(_, _) => stay
  }

  private def preparedForCandidate(state: Meta): Meta = {
    state.term = state.term.nextTerm
    state.nodes.map(t => t ! RequestVote(
      term = state.term.current,
      candidateId = self,
      lastLogIndex = state.log.lastIndex,
      lastLogTerm = state.log.lastTerm
    ))
    resetTimer
    state
  }

  private def preparedForLeader(state: Meta) = {
    state.nodes.map(node =>
      node ! AppendEntries(
        term = state.term.current,
        leaderId = self,
        prevLogIndex = state.log.lastIndex,
        prevLogTerm = state.log.lastTerm,
        entries = List(), // empty means NOOP
        leaderCommit = state.log.commitIndex)
    )
    resetTimer
    state
  }

  initialize() // akka specific

  /*
   *  --- Internals ---
   */

  private def sendEntries(data: Meta) = {
    data.nodes.filterNot(_ == self).map { node =>
      val message = compileMessage(node, data)
      node ! message
    }
  }

  private def resendTo(node: Raft.NodeId, data: Meta) = {
    val message = compileMessage(node, data)
    node ! message
  }

  private def compileMessage(node: ActorRef, data: Meta): AppendEntries = {
    val prevIndex = data.log.nextIndex(node) - 1
    val prevTerm = data.log.termOf(prevIndex)
    val fromMissing = data.log.lastIndex - prevIndex
    AppendEntries(
      term = data.term.current,
      leaderId = self,
      prevLogIndex = prevIndex,
      prevLogTerm = prevTerm,
      entries = data.log.entries.takeRight(fromMissing),
      leaderCommit = data.log.commitIndex
    )
  }

  private def writeToLog(clientRpc: ClientCommand, data: Meta) = {
    val entry = LogEntry(clientRpc.command, data.term.current) // TODO: only strings
    data.log = data.log.append(List(entry))
  }

  private def createPendingRequest(sender: ActorRef,
    clientRpc: ClientCommand, data: Meta) = {
    val ref = ClientRef(sender, clientRpc.id)
    val request = ClientRequest(clientRpc)
    data.requests = data.requests.add(ref, request)
  }

  private def resetTimer = {
    cancelTimer("timeout")
    setTimer("timeout", Timeout, 200 millis, false) // TODO: should pick random time
  }

  /*
   * AppendEntries handling 
   */
  private def append(rpc: AppendEntries, data: Meta): (AppendReply, Meta) = {
    if (leaderIsBehind(rpc, data)) appendFail(data)
    else if (!hasMatchingLogEntryAtPrevPosition(rpc, data)) appendFail(data)
    else appendSuccess(rpc, data)
  }

  private def leaderIsBehind(rpc: AppendEntries, data: Meta): Boolean =
    rpc.term < data.term.current

  private def hasMatchingLogEntryAtPrevPosition(
    rpc: AppendEntries, data: Meta): Boolean =
    (data.log.entries.isDefinedAt(rpc.prevLogIndex) &&
      (data.log.entries(rpc.prevLogIndex).term == rpc.prevLogTerm))

  private def appendFail(data: Meta) =
    (AppendFailure(data.term.current), data)

  private def appendSuccess(rpc: AppendEntries, data: Meta) = {
    // if newer entries exist in log these are not committed and can 
    // safely be removed - should add check during exhaustive testing
    // to ensure property holds
    data.log = data.log.append(rpc.entries, Some(rpc.prevLogIndex + 1))
    data.term = Term.max(data.term, Term(rpc.term))
    (AppendSuccess(data.term.current, data.log.lastIndex), data)
  }

  /*
   * Determine whether to grant or deny vote
   */
  private def vote(rpc: RequestVote, data: Meta): (Vote, Meta) =
    if (alreadyVoted(data)) deny(rpc, data)
    else if (rpc.term < data.term.current) deny(rpc, data)
    else if (rpc.term == data.term.current)
      if (candidateLogTermIsBehind(rpc, data)) deny(rpc, data)
      else if (candidateLogTermIsEqualButHasShorterLog(rpc, data)) deny(rpc, data)
      else grant(rpc, data) // follower and candidate are equal, grant
    else grant(rpc, data) // candidate is ahead, grant

  private def deny(rpc: RequestVote, data: Meta) = {
    data.term = Term.max(data.term, Term(rpc.term))
    (DenyVote(data.term.current), data)
  }
  private def grant(rpc: RequestVote, data: Meta): (Vote, Meta) = {
    data.votes = data.votes.vote(rpc.candidateId)
    data.term = Term.max(data.term, Term(rpc.term))
    (GrantVote(data.term.current), data)
  }

  private def candidateLogTermIsBehind(rpc: RequestVote, data: Meta) =
    data.log.entries.last.term > rpc.lastLogTerm

  private def candidateLogTermIsEqualButHasShorterLog(rpc: RequestVote, data: Meta) =
    (data.log.entries.last.term == rpc.lastLogTerm) &&
      (data.log.entries.length - 1 > rpc.lastLogIndex)

  private def alreadyVoted(data: Meta): Boolean = data.votes.votedFor match {
    case Some(_) => true
    case None => false
  }
}
