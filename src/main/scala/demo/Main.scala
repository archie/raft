package demo

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import scala.concurrent.Await
import scala.concurrent.duration._
import java.util.Calendar
import scala.language.postfixOps
import raft._

class Sequencer extends Actor with RaftClient with ActorLogging {
  import context._

  def schedule = system.scheduler.scheduleOnce(1000 millis, self, "sequence")

  override def preStart() = schedule
  override def postRestart(reason: Throwable) = {}

  def receive = {
    case "sequence" =>
      log.info(s"Got: $sequence at $time")
      schedule
  }

  def sequence: Int = Await.result(decide("get"), 2 seconds)
  def time = Calendar.getInstance().getTime()
}

object Main extends App {
  implicit val system = ActorSystem("raft")
  val members = Raft(5)
  val client = system.actorOf(Props[Sequencer], "client")

  println("Running raft demo - press enter key to exit")
  Console.readLine

  system.shutdown
}