package raft

import scala.language.implicitConversions
import akka.actor.ActorRef

case class InternalClientRef(sender: ActorRef, cid: Int)

case class Entry(
  val command: String,
  val term: Int,
  val client: Option[InternalClientRef] = None)

abstract class Entries(log: Vector[Entry]) {
  def persist(entries: Vector[Entry])

  def append(entries: Vector[Entry]): Vector[Entry] =
    append(entries, log.length)
  def append(entries: Vector[Entry], at: Int): Vector[Entry] = {
    val updlog = log.take(at) ++ entries
    persist(updlog)
    updlog
  }

  def termOf(index: Int): Int =
    if (index > 0) this(index).term
    else 0
  def lastIndex = log.length
  def lastTerm = termOf(lastIndex)
  def hasEntryAt(index: Int): Boolean = log.isDefinedAt(index - 1)

  def get(i: Int) = this(i) // how to use apply directly?
  def apply(index: Int): Entry = log(index - 1)
}

class InMemoryEntries[T](log: Vector[Entry]) extends Entries(log) {
  def persist(entries: Vector[Entry]) = ()
}

object InMemoryEntries {
  implicit def canBuildFrom(v: Vector[Entry]) = new InMemoryEntries(v)
}

case class Log(
    entries: Vector[Entry],
    nextIndex: Map[Raft.NodeId, Int],
    matchIndex: Map[Raft.NodeId, Int],
    commitIndex: Int = 0,
    lastApplied: Int = 0) {

  import InMemoryEntries._

  def decrementNextFor(node: Raft.NodeId) =
    copy(nextIndex = nextIndex + (node -> (nextIndex(node) - 1)))

  def resetNextFor(node: Raft.NodeId) =
    copy(nextIndex = nextIndex + (node -> (entries.lastIndex + 1)))

  def matchFor(node: Raft.NodeId, to: Option[Int] = None) = to match {
    case Some(toVal) => copy(matchIndex = matchIndex + (node -> toVal))
    case None => copy(matchIndex = matchIndex + (node -> (matchIndex(node) + 1)))
  }

  def commit(index: Int) = copy(commitIndex = index)
  def applied = copy(lastApplied = lastApplied + 1)
}

object Log {
  import InMemoryEntries._
  def apply(nodes: List[Raft.NodeId], entries: Vector[Entry]): Log = {
    val nextIndex = entries.lastIndex + 1
    val nextIndices = (for (n <- nodes) yield (n -> nextIndex)).toMap
    val matchIndices = (for (n <- nodes) yield (n -> 0)).toMap
    Log(entries, nextIndices, matchIndices)
  }
}