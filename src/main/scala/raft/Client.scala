package raft

class Client(var leader: Raft.NodeId) {
  def call(command: String): Unit = ???
}
