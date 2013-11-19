package raft

import org.scalatest._
import akka.testkit._
import akka.actor.ActorSystem

abstract class RaftSpec extends TestKit(ActorSystem()) with ImplicitSender
    with WordSpecLike with MustMatchers with BeforeAndAfterAll {
  override def afterAll = TestKit.shutdownActorSystem(system)
}

class RaftIntegrationSpec extends RaftSpec with BeforeAndAfterEach {

  val cluster = for (i <- List.range(0, 3)) yield TestFSMRef(new Raft())

  override def beforeEach = {
    cluster.map(n => n ! Init(cluster))
  }

  "a raft cluster" must {

    "elect a leader when first initialised" in (pending)
    "re-elect a leader if the leader crashes" in (pending)
    "replicate append entries accross the entire cluster" in (pending)
    "respond to client requests" in (pending)
    "forward client requests to the cluster leader" in (pending)

  }
}