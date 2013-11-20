package raft

import org.scalatest._
import akka.testkit._
import akka.actor.ActorSystem

abstract class RaftSpec extends TestKit(ActorSystem()) with ImplicitSender
    with WordSpecLike with MustMatchers with BeforeAndAfterAll {
  override def afterAll = TestKit.shutdownActorSystem(system)
}

class RaftIntegrationSpec extends RaftSpec with BeforeAndAfterEach {

  var cluster: List[TestFSMRef[Role, Meta, Raft]] = _

  override def beforeEach = {
    cluster = for (i <- List.range(0, 3)) yield TestFSMRef(new Raft())
    cluster.map(n => n ! Init(cluster))
  }

  override def afterEach = cluster.map(_.stop)

  "a raft cluster" must {

    "elect a leader when first initialised" in {
      Thread.sleep(2000)
      cluster.count(_.stateName == Leader) must be(1)
      cluster.count(_.stateName == Follower) must be(2)
      cluster.count(_.stateName == Candidate) must be(0)
    }

    "re-elect a leader if the leader crashes" in (pending)
    "replicate append entries accross the entire cluster" in (pending)
    "respond to client requests" in (pending)
    "forward client requests to the cluster leader" in (pending)

  }
}