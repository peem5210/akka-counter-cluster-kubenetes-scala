package akka.counter.cluster.kubernetes.queue.manager


object QueueStatus extends Enumeration {
  type QueueStatus = Value

  val Unknown: Value = Value(0)
  val Waiting: Value = Value(1)
  val SiteEntered: Value = Value(2)
  val QueueExited: Value = Value(3)
}


