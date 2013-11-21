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
      pending
      Thread.sleep(500)
      cluster.count(_.stateName == Leader) must be(1)
      cluster.count(_.stateName == Follower) must be(2)
      cluster.count(_.stateName == Candidate) must be(0)
    }

    "re-elect a leader if the leader crashes" in {
      pending
      Thread.sleep(500)
      val firstLeader = cluster.filter(_.stateName == Leader).head
      firstLeader.stop // kill leader
      Thread.sleep(2000) // new leader amongst remaining should be elected
      val newLeader = cluster.filter(n => !n.isTerminated && n.stateName == Leader)
      newLeader.length must be(1)
      newLeader.head must not be (firstLeader)
    }

    "replicate append entries accross the entire cluster" in {
      val client = TestProbe()
      val request = ClientRequest(100, "test")
      Thread.sleep(500)
      val leader = cluster.filter(_.stateName == Leader).head
      val leaderTerm = leader.stateData.term.current
      client.send(leader, request)
      Thread.sleep(100)
      leader.stateData.log.entries.length must be(1)
      cluster.map { n =>
        n.stateData.log.entries must contain(
          Entry(
            command = "test",
            term = leaderTerm,
            client = Some(InternalClientRef(client.ref, 100))))
      }
    }

    "respond to client requests" in (pending)
    "forward client requests to the cluster leader" in (pending)

  }
}