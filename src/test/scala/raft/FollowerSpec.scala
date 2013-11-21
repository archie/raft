package raft

import scala.language.postfixOps
import org.scalatest._
import akka.testkit._
import scala.concurrent.duration._

class FollowerSpec extends RaftSpec with BeforeAndAfterEach {

  var default: Meta = _
  var follower: TestFSMRef[Role, Meta, Raft] = _

  override def beforeEach = {
    default = Meta(
      term = Term(2),
      log = Log(List(probe.ref), List(LogEntry("a", 1), LogEntry("b", 2))),
      rsm = totalOrdering,
      nodes = List(probe.ref)
    )
    follower = TestFSMRef(new Raft())
  }

  val probe = TestProbe()
  val totalOrdering = new TotalOrdering

  "a follower" must {
    "not append entries if requester's term is less than current term" in {
      follower.setState(Follower, default)
      follower ! AppendEntries(
        term = 1,
        leaderId = testActor,
        prevLogIndex = 1,
        prevLogTerm = 1,
        entries = List(LogEntry("op", 1)),
        leaderCommit = 1)
      expectMsg(AppendFailure(2))
    }

    "not append entries if log doesn't contain an entry at " +
      "previous index" in {
        follower.setState(Follower, default)
        follower ! AppendEntries(
          term = 2,
          leaderId = testActor,
          prevLogIndex = 3, // this is one step ahead of follower.log.length-1
          prevLogTerm = 2,
          entries = List(LogEntry("op", 2)),
          leaderCommit = 0
        )
        expectMsg(AppendFailure(2))
      }

    "not append entries if log doesn't contain an entry at " +
      "previous index with matching terms" in {
        follower.setState(Follower, default)
        follower ! AppendEntries(
          term = 3,
          leaderId = testActor,
          prevLogIndex = 1, // last position in follower log
          prevLogTerm = 3,
          entries = List(LogEntry("op", 3)),
          leaderCommit = 0
        )
        expectMsg(AppendFailure(2)) // TODO: needs to include jump back info
      }

    "remove uncommitted entries if appending at a position" +
      "less than log length" in {
        val longlog = default.copy(log = default.log.append(List(LogEntry("remove", 2)), Some(2)))
        follower.setState(Follower, longlog)
        follower ! AppendEntries(
          term = 3,
          leaderId = testActor,
          prevLogIndex = 1, // matches follower's entry ("b", 2)
          prevLogTerm = 2, // matches follower's entry's term
          entries = List(LogEntry("op", 3)),
          leaderCommit = 0
        )
        expectMsg(AppendSuccess(3, 2))
        follower.stateData.log.entries must not contain (LogEntry("remove", 2))
      }

    "append entry if previous log index and term match" in {
      follower.setState(Follower, default)
      follower ! AppendEntries(
        term = 3,
        leaderId = testActor,
        prevLogIndex = 1, // matches follower's last entry
        prevLogTerm = 2, // matches follower's last entry's term
        entries = List(LogEntry("op", 3)),
        leaderCommit = 0
      )
      expectMsg(AppendSuccess(3, 2))
      follower.stateData.log.entries.last.command must be("op")
    }

    "append NOOP entries" in {
      follower.setState(Follower, default)
      follower ! AppendEntries(
        term = 3,
        leaderId = testActor,
        prevLogIndex = 1,
        prevLogTerm = 2,
        entries = List(),
        leaderCommit = 0
      )
      expectMsg(AppendSuccess(3, 1))
      follower.stateData.log.entries.last.command must be("b")
    }

    "append NOOP entries when log is empty (i.e., bootstrap)" in {
      val empty = Meta(
        term = Term(1),
        log = Log(List(probe.ref), List()),
        rsm = totalOrdering,
        nodes = List(probe.ref)
      )
      follower.setState(Follower, empty)
      follower ! AppendEntries(
        term = 2,
        leaderId = testActor,
        prevLogIndex = 0,
        prevLogTerm = 1,
        entries = List(),
        leaderCommit = 0
      )
      expectMsg(AppendSuccess(2, 0))
    }

    "append NOOP entries when log is empty in same term" in {
      val empty = Meta(
        term = Term(50),
        log = Log(List(probe.ref), List()),
        rsm = totalOrdering,
        nodes = List(probe.ref)
      )
      follower.setState(Follower, empty)
      follower ! AppendEntries(
        term = 50,
        leaderId = testActor,
        prevLogIndex = 0,
        prevLogTerm = 1,
        entries = List(),
        leaderCommit = 0
      )
      expectMsg(AppendSuccess(50, 0))
    }

    "append multiple entries if previous log index and term match" in {
      follower.setState(Follower, default)
      val probe = TestProbe()
      probe.send(follower, AppendEntries(
        term = 3,
        leaderId = testActor,
        prevLogIndex = 1, // matches follower's last entry
        prevLogTerm = 2, // matches follower's last entry's term
        entries = List(LogEntry("op", 3), LogEntry("op2", 3)),
        leaderCommit = 0
      ))
      probe.expectMsg(AppendSuccess(3, 3))
      follower.stateData.log.entries must contain(LogEntry("op", 3))
      follower.stateData.log.entries.last must be(LogEntry("op2", 3))
    }

    "grant vote if candidate term is higher to own term" in {
      follower.setState(Follower, default)
      follower ! RequestVote(3, testActor, 2, 2)
      expectMsg(GrantVote(3))
    }

    "grant vote if candidate is exactly equal" in {
      follower.setState(Follower, default)
      follower ! RequestVote(
        term = 2,
        candidateId = testActor,
        lastLogIndex = 1,
        lastLogTerm = 2
      )
      expectMsg(GrantVote(2))
    }

    "deny vote if own log's last term is more up to date than candidate" in {
      //      If the logs have last entries with different terms
      //      then the log with the later term is more up to date. 
      //      If the logs end with the same term, then whichever log is 
      //      longer (i.e., logIndex) is more up to date.
      follower.setState(Follower, default)
      follower ! RequestVote(
        term = 2,
        candidateId = testActor,
        lastLogIndex = 1,
        lastLogTerm = 1
      )
      expectMsg(DenyVote(2))
    }

    "deny vote if own log's last term is equal but log is longer than candidate" in {
      //      If the logs have last entries with different terms
      //      then the log with the later term is more up to date. 
      //      If the logs end with the same term, then whichever log is 
      //      longer (i.e., logIndex) is more up to date.
      val longlog = default.copy(log = default.log.append(List(LogEntry("c", 2)), Some(2)))
      follower.setState(Follower, longlog)
      follower ! RequestVote(
        term = 2,
        candidateId = testActor,
        lastLogIndex = 1, // shorter log than follower
        lastLogTerm = 2
      )
      expectMsg(DenyVote(2))
    }

    "deny vote if term is lower than own term" in {
      follower.setState(Follower, default)
      follower ! RequestVote(1, testActor, 2, 2)
      expectMsg(DenyVote(2))
    }

    "deny vote if vote for term already cast" in {
      val voted = default.copy(votes = default.votes.vote(probe.ref))
      follower.setState(Follower, voted)
      follower ! RequestVote(3, testActor, 2, 2)
      expectMsg(DenyVote(3))
    }

    "convert to candidate if no messages are received within timeout" in {
      follower.setState(Follower, default)
      follower.setTimer("timeout", Timeout, 1 millis, false)
      Thread.sleep(40)
      follower.stateName must be(Candidate)
    }

    "reset timer when granting vote" in {
      follower.setState(Follower, default)
      follower.setTimer("timeout", Timeout, 200 millis, false)
      Thread.sleep(150)
      follower ! RequestVote(3, testActor, 2, 2) // follower grants vote
      Thread.sleep(100) // ensures first timeout has expired 
      follower.stateName must be(Follower) // stays as follower
      follower.isTimerActive("timeout") must be(true)

    }

    "convert to candidate if timeout reached" in {
      follower.setState(Follower, default)
      follower.setTimer("timeout", Timeout, 200 millis, false)
      Thread.sleep(150)
      follower ! RequestVote(1, testActor, 2, 2) // follower denies this
      Thread.sleep(100) // ensures first timeout has expired 
      follower.stateName must be(Candidate) // timeout happened,
      // transition to candidate
    }

    "reset timeout after receiving AppendEntriesRPC" in {
      follower.setState(Follower, default)
      follower.setTimer("timeout", Timeout, 200 millis, false)
      Thread.sleep(150)
      follower ! AppendEntries(3, testActor, 1, 2, List(LogEntry("op", 3)), 0)
      Thread.sleep(100) // ensures first timeout has expired 
      follower.stateName must be(Follower) // stays as follower
      follower.isTimerActive("timeout") must be(true)
    }

    "increase its term when transitioning to candidate" in {
      follower.setState(Follower, default)
      follower.setTimer("timeout", Timeout, 1 millis, false)
      follower.stateData.term.current must be(2)
      Thread.sleep(30)
      follower.stateData.term.current must be(3) // is candidate by now
    }

    "increase own term if RequestVote contains higher term" in {
      follower.setState(Follower, default)
      follower ! RequestVote(3, testActor, 1, 1) // higher term
      follower.stateName must be(Follower)
      follower.stateData.term.current must be(3)
    }

    "increase own term if AppendEntries contains higher term" in {
      follower.setState(Follower, default)
      follower ! AppendEntries(3, testActor, 1, 2, List(LogEntry("op", 3)), 0) // higher term
      follower.stateName must be(Follower)
      follower.stateData.term.current must be(3)
    }
  }
}