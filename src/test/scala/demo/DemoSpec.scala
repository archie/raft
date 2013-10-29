//package demo
//
//import org.scalatest._
//import akka.testkit._
//import akka.actor.ActorSystem
//
//abstract class FSMSpec extends TestKit(ActorSystem()) with ImplicitSender
//	with WordSpecLike with MustMatchers with BeforeAndAfterAll {
//  override def afterAll = TestKit.shutdownActorSystem(system)
//}
//
//class DemoSpec extends FSMSpec {
//  val demo = TestFSMRef(new Demo())
//  
//  "the demo fsm" must {
//    "wake up when sufficiently hungry" in {
//      
//      demo ! Eat
//      demo ! Eat 
//      demo.stateData must be(Hunger(2))
//      
//      demo ! Eat 
//      demo ! Eat
//      demo ! Eat // waking up 
//      expectMsg("You're a good guy!")
//      
//      demo.stateName must be(Awake)
//    }
//  }
//	
//}