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
}

case class Meta[C[T]](
    term: Term,
    log: List[LogEntry], // TODO: Extract to Log class
    rsm: ReplicatedStateMachine[C],
    requests: Requests = Requests(),
    votes: Votes = Votes()) {
  def votedFor(ref: Raft.NodeId) = votes.votedFor match {
    case Some(vote) => this
    case None =>
      val updVotes = votes.copy(votedFor = Some(ref))
      // TODO: Persist this value before returning
      this.copy(votes = updVotes)
  }
}

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

case class ConsensusData() {
  private[raft] var currentTerm: Int = 0
  def term = currentTerm
  def nextTerm: ConsensusData = ???
  def votedFor(node: Raft.NodeId): ConsensusData = ???

  def pending(ref: ClientRef, request: ClientRequest): ConsensusData = ???
  def incrementPending(ref: ClientRef): ConsensusData = ???
  def hasAppendMajority(ref: ClientRef): Boolean = ???
  def removePending(ref: ClientRef): ConsensusData = ???

  def addVote(): ConsensusData = ???
}

object StateFactory {

}
