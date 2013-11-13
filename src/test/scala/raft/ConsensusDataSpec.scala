package raft

import akka.testkit._
import org.scalatest._

class ConsensusDataSpec extends RaftSpec with WordSpecLike
    with MustMatchers with BeforeAndAfterEach {

  var state: Meta[Command] = _

  //  state.term.current
  //  state.votes.received
  //  state.requests.pending
  //  state.log.*
  //  state.rsm.execute()

  override def beforeEach = state = Meta(Term(1), List(), new TotalOrdering)

  "meta" must {
    "keep at most one vote for a candidate per term" in {
      // state.storeVote(ref)
      pending
    }
  }

  "term" must {
    "increase term monotonically" in {
      val t = Term(1)
      val t1 = t.nextTerm
      t1 must be(Term(2))
      val t2 = t1.nextTerm
      t2 must be(Term(3))
    }
    "compare to terms against each other" in {
      val t1 = Term(1)
      val t2 = Term(2)
      t1 must be < t2
    }
    "find the max term given two terms" in {
      val t1 = Term(1)
      val t2 = Term(2)
      Term.max(t1, t2) must be(t2)
      Term.max(t2, t1) must be(t2)
    }
  }

  "votes" must {
    "keep track of votes received" in {
      val probe = TestProbe()
      val v = Votes()
      val v2 = v.gotVoteFrom(probe.ref)
      v2.received must have length (1)
    }
    "check if majority votes received" in {
      val probe = TestProbe()
      val v = Votes(received = List(probe.ref, probe.ref))
      v.hasMajority(5) must be(false)
      v.hasMajority(3) must be(true)
    }
  }

  "client requests" must {
    "store pending requests" in (pending)
    "increase append success count per request" in (pending)
    "check if majority has been reached per request" in (pending)
    "delete requests that have been replied to" in (pending)
  }

  "log" must {
    "maintain a next index for each follower" in (pending)
    "decrement the next index for a follower if older log entries must be passed" in (pending)
    "set the next index for a follower based on the last log entry sent" in (pending)
    "TODO: match index" in (pending)
  }

  "replicated state machine" must {
    "apply commands to a generic state machine" in (pending)
    "keep track of the log index of the last command applied to the state machine" in (pending)
  }

  "state factory" must {
    "create a state object from file" in (pending)
    "persist a state object to file" in (pending)
  }
}