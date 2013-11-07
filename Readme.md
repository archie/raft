# Raft in Scala

**NOTE: This is a work-in-progress** 

## Background

### What is Raft?

[Raft](http://raftconsensus.github.io/) is a distributed consensus algorithm developed by [Diego Ongaro](https://twitter.com/ongardie) and [John Ousterhout](http://www.stanford.edu/~ouster/). Its original implementation was done by Diego in C++ and is called [LogCabin](https://github.com/logcabin/logcabin).  

### What is consensus? 

In a nutshell, consensus is about getting a set of computers to agree on a single value for a particular moment in time, and will continue to do so correctly as long as a majority of the machines remain available. Except for in politics, this may seem a trivial problem to solve. However, in 1985 a group of researchers proved that under specific conditions this is in theory [impossible to solve](http://dl.acm.org/citation.cfm?id=214121). Luckily the world is not that rough and some randomness helps a long way. 

The classroom algorithm for styding consensus in distributed systems has for a long time been Paxos developed by [Leslie Lamport](http://research.microsoft.com/en-us/um/people/lamport/). It is also known for being notoriously difficult to implement.

### Why implement Raft in Scala and Akka?

Together with [Nick Rutherford](https://github.com/nruth/gaoler), I implemented Paxos using Erlang as part of our master's programme. We have first-hand experience on the tricky implementation details of Paxos, fighting furiously to get the [paper's definition](http://research.microsoft.com/en-us/um/people/lamport/pubs/lamport-paxos.pdf) to a working algorithm. Raft is designed to be easier to understand and I wanted to test that argument during my time at [Hacker School](http://hackerschool.com). 

Interestingly, there are [quite a few open source implementations](http://raftconsensus.github.io/#implementations) of Raft in a wide variety of languages, but as of yet (Nov 1, 2013) none in Scala and Akka.

Moreover, Akka suitable for writing distributed applications and Scala is a pretty neat language that I've been wanting to explore further.  

## Progress

[![Build Status](https://travis-ci.org/archie/raft.png)](https://travis-ci.org/archie/raft)

* 6/11/13 - Adding plugin [scalariform](https://github.com/mdr/scalariform) to maintain code style, fixing remaining follower test cases.
* 5/11/13 - Candidate state completed.
* 31/10/13 - Most of follower state is complete. It is missing ability to increase term if receiving message with a higher term number. 

## Installing

Make sure you have at least Scala 2.10.2 and the build tool [sbt](http://www.scala-sbt.org/) installed. 

1. Clone this repo
2. Run the tests `sbt test`

That is essentially all for now. 

## Configuration

_TBD_

## Contributions

Pull requests, small and large, are more than welcome. Whether it is a suggestion for writing more idiomatic Scala, better test cases, or a bug in the implementation - feel free to fire away! 

## Endnote

License: _TBD_

Made at [Hacker School](http://hackerschool.com).

(c) Marcus Ljungblad, 2013

