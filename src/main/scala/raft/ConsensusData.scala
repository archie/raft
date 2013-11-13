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

case class Requests(pending: Map[ClientRef, ClientRequest] = Map())
case class Votes(
    votedFor: Option[Raft.NodeId] = None,
    received: List[Raft.NodeId] = List()) {
  def gotVoteFrom(ref: ActorRef): Votes = this.copy(received = ref :: received)
  def hasMajority(size: Int): Boolean =
    (this.received.length >= Math.ceil(size / 2.0))
}

case class Meta[C[T]](
  term: Term,
  log: List[LogEntry], // TODO: Extract to Log class
  rsm: ReplicatedStateMachine[C],
  requests: Requests = Requests(),
  votes: Votes = Votes())

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
