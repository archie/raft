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

// this might go elsewhere later
case class LogEntry(entry: String, term: Raft.Term,
  sender: Option[ClientRef] = None)

case class Log(
    entries: List[LogEntry],
    nextIndex: Map[Raft.NodeId, Int],
    matchIndex: Map[Raft.NodeId, Int],
    commitIndex: Int = 0,
    lastApplied: Int = 0) {

  def decrementNextFor(node: Raft.NodeId) =
    copy(nextIndex = nextIndex + (node -> (nextIndex(node) - 1)))

  def resetNextFor(node: Raft.NodeId) =
    copy(nextIndex = nextIndex + (node -> entries.length))

  def matchFor(node: Raft.NodeId, to: Option[Int] = None) = to match {
    case Some(toVal) => copy(matchIndex = matchIndex + (node -> toVal))
    case None => copy(matchIndex = matchIndex + (node -> (matchIndex(node) + 1)))
  }

  def lastIndex = if (entries.length > 0) entries.length - 1 else 0

  def lastTerm = if (entries.length > 0) entries.last.term else 1

  def termOf(index: Int) = entries(index).term

  def append(incoming: List[LogEntry], at: Option[Int] = None) = at match {
    case None => copy(entries = entries ::: incoming)
    case Some(pos) => copy(entries = entries.take(pos) ::: incoming)
  }

  def commit(index: Int) = copy(commitIndex = index)

  def applied = copy(lastApplied = lastApplied + 1)
}

object Log {
  def apply(nodes: List[Raft.NodeId], entries: List[LogEntry]): Log = {
    val nextIndex = entries.length
    val nextIndices = (for (n <- nodes) yield (n -> nextIndex)).toMap
    val matchIndices = (for (n <- nodes) yield (n -> 0)).toMap
    Log(entries, nextIndices, matchIndices)
  }
}

case class Meta(
  var term: Term,
  var log: Log,
  rsm: TotalOrdering, // TODO: Make generic
  var nodes: List[Raft.NodeId],
  var votes: Votes = Votes())

object Meta {
  def apply(nodes: List[Raft.NodeId]): Meta =
    Meta(Term(1), Log(nodes, List()), new TotalOrdering, nodes) // TODO: Lots
}

/* state data */
//case class Data(
//    currentTerm: Raft.Term, // persisted all states
//    votedFor: Option[Raft.NodeId], // persisted all states
//    log: List[LogEntry], // persisted all states
//    commitIndex: Int, // volatile all states
//    lastApplied: Int, // volatile all states
//
//    // leaders only
//    nextIndex: Map[Raft.NodeId, Int] = Map(), // volatile
//    matchIndex: Map[Raft.NodeId, Int] = Map(), // volatile 
//    pendingRequests: Map[ClientRef, ClientRequest] = Map(), // volatile
//
//    // candidates only 
//    votesReceived: List[Raft.NodeId] = List(), // volatile
//
//    // config data
//    nodes: List[Raft.NodeId] = List() // persistent
//    )

object StateFactory {

}
