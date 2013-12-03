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
import scala.util.Success
import scala.util.Failure

class Sequencer extends Actor with RaftClient with ActorLogging {
  import context._

  def schedule = system.scheduler.scheduleOnce(500 millis, self, "sequence")

  override def preStart() = schedule
  override def postRestart(reason: Throwable) = {}

  def receive = {
    case "sequence" =>
      decide("get") onComplete {
        case Success(x) => log.info(s"Got $x at $time")
        case Failure(t) => throw t
      }
      //system.scheduler.scheduleOnce(200 millis)(system.shutdown)
      schedule
  }

  def time = Calendar.getInstance().getTime()
}

object Main extends App {
  implicit val system = ActorSystem("raft")
  val members = Raft(3)
  val client = system.actorOf(Props[Sequencer], "client")

  println("Running raft demo - press enter key to exit")
  Console.readLine

  system.shutdown
}