# Raft in Scala

This is a partial implementation of [Raft](http://raftconsensus.github.io/) using [Scala](http://www.scala-lang.org/) and [Akka](http://akka.io/). As of now it has support for leader election and log replication. The outstanding issues are log compaction and cluster membership changes. 

[![Build Status](https://travis-ci.org/archie/raft.png)](https://travis-ci.org/archie/raft)

## Example

The following code snippet illustrates how to implement a client which periodically asks a Raft cluster for sequence numbers. Hence, the state machine that Raft executes commands against implements [total ordering](https://github.com/archie/raft/blob/master/src/main/scala/raft/ReplicatedStateMachine.scala). Most of the magic is deferred to [RaftClient](https://github.com/archie/raft/blob/master/src/main/scala/raft/Client.scala) which provides the interface to the [Raft](https://github.com/archie/raft/blob/master/src/main/scala/raft/Raft.scala) cluster.

```scala
class Sequencer extends Actor with RaftClient with ActorLogging {
  import context._

  def schedule = system.scheduler.scheduleOnce(1000 millis, self, "sequence")

  override def preStart() = schedule
  override def postRestart(reason: Throwable) = {}

  def receive = {
    case "sequence" =>
      log.info("Requesting sequence number")
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
```

This demo sets up a Raft cluster with five nodes on the same machine. In the future support for [remote](http://doc.akka.io/docs/akka/snapshot/scala/remoting.html) nodes will be added. 

## Background

### What is Raft?

[Raft](http://raftconsensus.github.io/) is a distributed consensus algorithm developed by [Diego Ongaro](https://twitter.com/ongardie) and [John Ousterhout](http://www.stanford.edu/~ouster/). Its original implementation was done by Diego in C++ and is called [LogCabin](https://github.com/logcabin/logcabin).  

### What is consensus? 

In a nutshell, consensus is about getting a set of computers to agree on a single value for a particular moment in time, and will continue to do so correctly as long as a majority of the machines remain available. Except for in politics, this may seem a trivial problem to solve. However, in 1985 a group of researchers proved that under specific conditions this is in theory [impossible to solve](http://dl.acm.org/citation.cfm?id=214121). Luckily the world is not that rough and some randomness helps a long way. 

The classroom algorithm for styding consensus in distributed systems has for a long time been Paxos developed by [Leslie Lamport](http://research.microsoft.com/en-us/um/people/lamport/). It is also known for being notoriously difficult to implement.

### Why implement Raft in Scala and Akka?

Together with [Nick Rutherford](https://github.com/nruth/gaoler), I implemented Paxos using Erlang as part of our master's programme. We have first-hand experience on the tricky implementation details of Paxos, fighting furiously to get the [paper's definition](http://research.microsoft.com/en-us/um/people/lamport/pubs/lamport-paxos.pdf) to a working algorithm. Raft is designed to be easier to understand and I wanted to test that argument during my time at [Hacker School](http://hackerschool.com). 

Interestingly, there are [quite a few open source implementations](http://raftconsensus.github.io/#implementations) of Raft in a wide variety of languages, and at least [one other](https://github.com/pablosmedina/ckite) in Scala using Finagle. 

Moreover, Akka suitable for writing distributed applications and Scala is a pretty neat language that I've been wanting to explore further.  

## Install

Make sure you have at least Scala 2.10.2 and the build tool [sbt](http://www.scala-sbt.org/) installed. 

1. Clone this repo
2. Run the tests `sbt test`. sbt will download the necessary dependencies automatically. 
3. Run the demo in the example above using `sbt run`

## Configuration

The only configuration as of now is maintained in `src/main/resources/application.conf`. 

## Contributions

Pull requests, small and large, are more than welcome. Whether it is a suggestion for writing more idiomatic Scala, better test cases, or a bug in the implementation - feel free to fire away! 

### Todo

* Log compaction
* Membership changes
* Remoting support
* Better/more examples

## Endnote

License: [MIT](https://github.com/archie/raft/blob/master/LICENSE)

Made at [Hacker School](http://hackerschool.com).

(c) Marcus Ljungblad, 2013

