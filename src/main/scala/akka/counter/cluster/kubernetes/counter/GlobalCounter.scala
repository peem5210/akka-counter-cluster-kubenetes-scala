package akka.counter.cluster.kubernetes.counter

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object GlobalCounter {
  final val name = "GlobalCounter"

  sealed trait Command

  case object Increment extends Command

  final case class GetValue(replyTo: ActorRef[Int]) extends Command

  final case class RetrieveAndInc(replyTo: ActorRef[Int]) extends Command

  case object GoodByeCounter extends Command

  def apply(): Behavior[Command] = {
    def updated(value: Int): Behavior[Command] = {
      Behaviors.receiveMessage[Command] {
        case Increment =>
          updated(value + 1)
        case GetValue(replyTo) =>
          replyTo ! value
          Behaviors.same
        case RetrieveAndInc(replyTo) =>
          replyTo ! value
          updated(value + 1)
        case GoodByeCounter =>
          // Possible async action then stop
          Behaviors.stopped
      }
    }

    updated(0)
  }
}
