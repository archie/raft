package raft

import org.scalatest._
import akka.testkit._
import akka.actor.ActorSystem

abstract class RaftSpec extends TestKit(ActorSystem()) with ImplicitSender
    with WordSpecLike with MustMatchers with BeforeAndAfterAll {
  override def afterAll = TestKit.shutdownActorSystem(system)
}