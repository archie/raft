package raft

import scala.language.higherKinds
import akka.actor.ActorRef

case class Term(current: Int, leader: Option[ActorRef] = None) extends Ordered[Term] {
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

case class Meta(
    var term: Term,
    var log: Log,
    rsm: TotalOrdering, // TODO: Make generic
    var nodes: List[Raft.NodeId],
    var votes: Votes = Votes()) {

  import InMemoryEntries._

  def leaderAppend(ref: ActorRef, e: Vector[Entry]) = {
    val entries = log.entries.append(e)
    log = log.copy(entries = entries)
    log = log.resetNextFor(ref)
    log = log.matchFor(ref, Some(log.entries.lastIndex))
  }

  def append(e: Vector[Entry], at: Int) =
    log = log.copy(entries = log.entries.append(e, at))

  def selectTerm(other: Term) =
    term = Term.max(this.term, other)

  def nextTerm =
    term = term.nextTerm

  def setLeader(leader: Raft.NodeId) =
    term = term.copy(leader = Some(leader))
}

object Meta {
  def apply(nodes: List[Raft.NodeId]): Meta =
    Meta(Term(0), Log(nodes, Vector[Entry]()), new TotalOrdering, nodes)
}
