package raft

import scala.language.higherKinds
import akka.actor.ActorRef

case class Term(current: Int) extends Ordered[Term] {
  def nextTerm: Term = this.copy(current = current + 1)
  def compare(that: Term) = current.compare(that.current)
}

object Term {
  def max(t1: Term, t2: Term): Term =
    if (t1 > t2) t1
    else t2
}

case class Requests(pending: Map[ClientRef, ClientRequest] = Map()) {
  def add(ref: ClientRef, req: ClientRequest) = this.copy(pending = pending + (ref -> req))
  def remove(ref: ClientRef) = this.copy(pending = pending - ref)
  def tick(ref: ClientRef) = pending.get(ref) match {
    case Some(req) =>
      val updRequest = req.copy(successes = req.successes + 1)
      val updPending = pending + (ref -> updRequest)
      this.copy(pending = updPending)
    case None => this
  }
  def majority(ref: ClientRef, size: Int) = pending.get(ref) match {
    case Some(req) if req.successes >= Math.ceil(size / 2.0) => true
    case _ => false
  }
}

case class Votes(
    votedFor: Option[Raft.NodeId] = None,
    received: List[Raft.NodeId] = List()) {
  def gotVoteFrom(ref: ActorRef): Votes = this.copy(received = ref :: received)
  def majority(size: Int): Boolean =
    (this.received.length >= Math.ceil(size / 2.0))
  def vote(ref: Raft.NodeId) = votedFor match {
    case Some(vote) => this
    case None => copy(votedFor = Some(ref)) // TODO: Persist this value before returning
  }
}

case class Log(
    entries: List[LogEntry],
    nextIndex: Map[Raft.NodeId, Int],
    matchIndex: Map[Raft.NodeId, Int]) {
  def decrementNextFor(node: Raft.NodeId) =
    copy(nextIndex = nextIndex + (node -> (nextIndex(node) - 1)))
  def resetNextFor(node: Raft.NodeId, to: Int) =
    copy(nextIndex = nextIndex + (node -> to))
  def matchFor(node: Raft.NodeId, to: Option[Int] = None) = to match {
    case Some(toVal) => copy(matchIndex = matchIndex + (node -> toVal))
    case None => copy(matchIndex = matchIndex + (node -> (matchIndex(node) + 1)))
  }
}

object Log {
  def apply(nodes: List[Raft.NodeId], entries: List[LogEntry]): Log = {
    val nextIndex = entries.length
    val nextIndices = (for (n <- nodes) yield (n -> nextIndex)).toMap
    val matchIndices = (for (n <- nodes) yield (n -> 0)).toMap
    Log(entries, nextIndices, matchIndices)
  }
}

case class Meta[C[T]](
  var term: Term,
  log: List[LogEntry], // TODO: Extract to Log class
  rsm: ReplicatedStateMachine[C],
  var requests: Requests = Requests(),
  var votes: Votes = Votes())

/* state data */
case class Data(
    currentTerm: Raft.Term, // persisted all states
    votedFor: Option[Raft.NodeId], // persisted all states
    log: List[LogEntry], // persisted all states
    commitIndex: Int, // volatile all states
    lastApplied: Int, // volatile all states

    // leaders only
    nextIndex: Map[Raft.NodeId, Int] = Map(), // volatile
    matchIndex: Map[Raft.NodeId, Int] = Map(), // volatile 
    pendingRequests: Map[ClientRef, ClientRequest] = Map(), // volatile

    // candidates only 
    votesReceived: List[Raft.NodeId] = List(), // volatile

    // config data
    nodes: List[Raft.NodeId] = List() // persistent
    ) {

}

object StateFactory {

}
