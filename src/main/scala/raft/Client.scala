package raft

import akka.actor.Actor
import akka.pattern.ask
import scala.concurrent.{ Future, Await }
import scala.concurrent.duration._
import scala.language.postfixOps

trait RaftClient {
  self: Actor =>
  import context._

  import akka.util.Timeout
  implicit val tc = Timeout(2 seconds)

  private var cid: Int = 0
  def tick = {
    cid = cid + 1
    cid
  }

  def raftMember = system.actorSelection("/member*").resolveOne

  def decide(command: String): Future[Int] =
    for {
      member <- raftMember
      reply <- (member ? ClientRequest(tick, command)).mapTo[(Int, Int)]
    } yield reply._2
}

