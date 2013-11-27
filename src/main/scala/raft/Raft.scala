package raft

import scala.language.postfixOps
import akka.actor.{ Actor, ActorRef, FSM, LoggingFSM }
import scala.concurrent.duration._
import scala.concurrent.Promise
import math.random

/* messages */
sealed trait Message
case object Timeout extends Message
case object Heartbeat extends Message
case class Init(nodes: List[NodeId]) extends Message

sealed trait Request extends Message
case class RequestVote(
  term: Term,
  candidateId: NodeId,
  lastLogIndex: Int,
  lastLogTerm: Term) extends Request

case class AppendEntries(
  term: Term,
  leaderId: NodeId,
  prevLogIndex: Int,
  prevLogTerm: Term,
  entries: Vector[Entry],
  leaderCommit: Int) extends Request

sealed trait Vote extends Message
case class DenyVote(term: Term) extends Vote
case class GrantVote(term: Term) extends Vote

sealed trait AppendReply extends Message
case class AppendFailure(term: Term) extends AppendReply
case class AppendSuccess(term: Term, index: Int) extends AppendReply

case class ClientRequest(cid: Int, command: String) extends Message

/* states */
sealed trait Role
case object Leader extends Role
case object Follower extends Role
case object Candidate extends Role
case object Initialise extends Role

/* Consensus module */
class Raft() extends Actor with LoggingFSM[Role, Meta] {
  override def logDepth = 12
  import InMemoryEntries._

  startWith(Initialise, Meta(List()))

  when(Initialise) {
    case Event(cluster: Init, _) => goto(Follower) using initialised(cluster)
  }

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
    case Event(GrantVote(term), data: Meta) =>
      data.votes = data.votes.gotVoteFrom(sender)
      if (data.votes.majority(data.nodes.length))
        goto(Leader) using preparedForLeader(data)
      else stay using data
    case Event(DenyVote(term), data: Meta) =>
      if (term > data.term)
        goto(Follower) using preparedForFollower(data)
      else stay

    case Event(rpc: RequestVote, data) if (rpc.term == data.term) =>
      val (msg, upd) = grant(rpc, data)
      stay using (upd) replying msg

    // other   
    case Event(rpc: AppendEntries, data: Meta) =>
      val (msg, upd) = append(rpc, data)
      goto(Follower) using preparedForFollower(data) replying msg
    case Event(Timeout, data: Meta) =>
      goto(Candidate) using preparedForCandidate(data)
  }

  when(Leader) {
    case Event(clientRpc: ClientRequest, data: Meta) =>
      writeToLog(sender, clientRpc, data)
      sendEntries(data)
      stay using data
    case Event(rpc: AppendSuccess, data: Meta) =>
      data.log = data.log.resetNextFor(sender)
      data.log = data.log.matchFor(sender, Some(rpc.index))
      commitEntries(rpc, data)
      applyEntries(data)
      stay
    case Event(rpc: AppendFailure, data: Meta) =>
      if (rpc.term <= data.term) {
        log.debug(s"Decrementing and resending entries to follower: $sender")
        data.log = data.log.decrementNextFor(sender)
        resendTo(sender, data)
        stay
      } else {
        data.term = rpc.term
        goto(Follower) using preparedForFollower(data)
      }
    case Event(Heartbeat, data: Meta) =>
      sendEntries(data)
      stay
  }

  whenUnhandled {
    case Event(_, _) => stay // drop event
  }

  onTransition {
    case Leader -> Follower =>
      cancelTimer("heartbeat")
      resetTimer
    case Candidate -> Follower => resetTimer
    case Initialise -> Follower => resetTimer
  }

  private def preparedForFollower(state: Meta): Meta = {
    state.votes = Votes()
    state
  }

  private def preparedForCandidate(data: Meta): Meta = {
    data.nextTerm
    data.nodes.map { t =>
      t ! RequestVote(
        term = data.term,
        candidateId = self,
        lastLogIndex = data.log.entries.lastIndex,
        lastLogTerm = data.log.entries.lastTerm)
    }
    resetTimer
    data
  }

  private def preparedForLeader(state: Meta) = {
    sendEntries(state)
    state
  }

  private def initialised(cluster: Init): Meta = Meta(cluster.nodes)

  private def resetHeartbeatTimer = {
    cancelTimer("heartbeat")
    val nextTimeout = (random * 100).toInt + 100
    setTimer("heartbeat", Heartbeat, nextTimeout millis, false)
  }

  private def resetTimer = {
    cancelTimer("timeout")
    val nextTimeout = (random * 100).toInt + 200
    setTimer("timeout", Timeout, nextTimeout millis, false)
  }

  initialize() // akka specific

  /*
   *  --- Internals ---
   */

  private def applyEntries(data: Meta) =
    for (i <- data.log.lastApplied until data.log.commitIndex) {
      val entry = data.log.entries(i)
      val result = data.rsm.execute(Get) // TODO: make generic
      data.log = data.log.applied

      entry.client match {
        case Some(ref) => ref.sender ! (ref.cid, result)
        case None => // ignore
      }
    }

  private def commitEntries(rpc: AppendSuccess, data: Meta) = {
    if (rpc.index >= data.log.commitIndex &&
      data.log.entries.termOf(rpc.index) == data.term) {
      val matches = data.log.matchIndex.count(_._2 == rpc.index)
      if (matches >= Math.ceil(data.nodes.length / 2.0))
        data.log = data.log.commit(rpc.index)
    }
  }

  private def sendEntries(data: Meta) = {
    resetHeartbeatTimer
    data.nodes.filterNot(_ == self).map { node =>
      val message = compileMessage(node, data)
      node ! message
    }
  }

  private def resendTo(node: NodeId, data: Meta) = {
    val message = compileMessage(node, data)
    node ! message
  }

  private def compileMessage(node: ActorRef, data: Meta): AppendEntries = {
    val prevIndex = data.log.nextIndex(node) - 1
    val prevTerm = data.log.entries.termOf(prevIndex)
    val fromMissing = missingRange(data.log.entries.lastIndex, prevIndex)
    AppendEntries(
      term = data.term,
      leaderId = self,
      prevLogIndex = prevIndex,
      prevLogTerm = prevTerm,
      entries = data.log.entries.takeRight(fromMissing),
      leaderCommit = data.log.commitIndex
    )
  }

  private def missingRange(lastIndex: Int, prevIndex: Int) =
    if (prevIndex == 0) 1
    else lastIndex - prevIndex

  private def writeToLog(sender: NodeId, rpc: ClientRequest, data: Meta) = {
    val ref = InternalClientRef(sender, rpc.cid)
    val entry = Entry(rpc.command, data.term, Some(ref))
    data.leaderAppend(self, Vector(entry))
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
    rpc.term < data.term

  private def hasMatchingLogEntryAtPrevPosition(
    rpc: AppendEntries, data: Meta): Boolean =
    (rpc.prevLogIndex == 0 || // guards for bootstrap case
      (data.log.entries.hasEntryAt(rpc.prevLogIndex) &&
        (data.log.entries.termOf(rpc.prevLogIndex) == rpc.prevLogTerm)))

  private def appendFail(data: Meta) =
    (AppendFailure(data.term), data)

  private def appendSuccess(rpc: AppendEntries, data: Meta) = {
    data.append(rpc.entries, rpc.prevLogIndex)
    data.selectTerm(rpc.term)
    (AppendSuccess(data.term, data.log.entries.lastIndex), data)
  }

  /*
   * Determine whether to grant or deny vote
   */
  private def vote(rpc: RequestVote, data: Meta): (Vote, Meta) =
    if (alreadyVoted(data)) deny(rpc, data)
    else if (rpc.term < data.term) deny(rpc, data)
    else if (rpc.term == data.term)
      if (candidateLogTermIsBehind(rpc, data)) deny(rpc, data)
      else if (candidateLogTermIsEqualButHasShorterLog(rpc, data)) deny(rpc, data)
      else grant(rpc, data) // follower and candidate are equal, grant
    else grant(rpc, data) // candidate is ahead, grant

  private def deny(rpc: RequestVote, data: Meta) = {
    data.term = Term.max(data.term, rpc.term)
    (DenyVote(data.term), data)
  }
  private def grant(rpc: RequestVote, data: Meta): (Vote, Meta) = {
    data.votes = data.votes.vote(rpc.candidateId)
    data.term = Term.max(data.term, rpc.term)
    (GrantVote(data.term), data)
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
