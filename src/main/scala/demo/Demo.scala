package demo

import akka.actor.Actor
import akka.actor.FSM
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.ActorRef

sealed trait State
case object Awake extends State
case object Sleeping extends State
case object Whining extends State

sealed trait Need
case object Eat extends Need
case object Sleep extends Need
case object Complain extends Need

sealed trait Data
case class Hunger(level: Int) extends Data

class Demo extends Actor with FSM[State, Data]{

  startWith(Sleeping, Hunger(0))
  
  when(Sleeping) {
    case Event(Eat, h: Hunger) =>
      val hunger = Hunger(h.level + 1)
      if (hunger.level == 5) {
        println("too hungry, " + context.sender.toString + " woke me up")
        goto(Awake) using Hunger(0) replying("You're a good guy!")
      } else {
        println("hunger level increasing")
        stay using hunger
      }
  }
  
  when(Awake) {
    case Event(Sleep, d) =>
      println("tired, sleeping again")
      goto(Sleeping) using d
  }
  
  when(Whining) {
    case Event(Eat, d) =>
      println("I'm cranky allright")
      goto(Awake) using d
  }
  
  whenUnhandled {
    case Event(Complain, d) => 
      goto(Whining) using d
    case Event(e, d) =>
      println("don't know what to do")
      stay
  }
  
  onTransition {
    case (Whining | Sleeping) -> Awake => 
      stateData match {
        case h: Hunger =>
          for (x <- 0 to h.level)
            println("\t munch")
      }
  }
  
  initialize()
}


class Feeder(demo: ActorRef) extends Actor {
  def receive = {
    case "feed" => 
      demo ! Eat
			demo ! Eat
			demo ! Eat
			demo ! Eat
			demo ! Eat // waking up at this point
			demo ! Eat // don't know how to eat while awake
			demo ! Sleep // going back to sleeping
			demo ! Eat
			demo ! Complain
			demo ! Eat
    case msg => 
      println("Got: " + msg)
  }
}


object Main {
  def main(args: Array[String]) {
    val system = ActorSystem("fsm")
	
    val demo = system.actorOf(Props[Demo], name = "demofsm")
		val feeder = system.actorOf(Props(classOf[Feeder], demo), name = "feeder")
		feeder ! "feed"
		
		Thread.sleep(1000)
	
		system.shutdown
  }
}