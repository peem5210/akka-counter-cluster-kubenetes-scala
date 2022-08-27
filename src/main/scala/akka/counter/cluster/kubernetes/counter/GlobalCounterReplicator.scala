package akka.counter.cluster.kubernetes.counter

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator}
import akka.cluster.ddata.{PNCounter, PNCounterKey, SelfUniqueAddress}

object GlobalCounterReplicator {
  sealed trait Command
  case object Increment extends Command
  case object Decrement extends Command
  final case class GetValue(replyTo: ActorRef[Int]) extends Command
  final case class RetrieveAndInc(replyTo: ActorRef[Int]) extends Command
  final case class GetCachedValue(replyTo: ActorRef[Int]) extends Command
  case object Unsubscribe extends Command

  private sealed trait InternalCommand extends Command
  private case class InternalUpdateResponse(rsp: Replicator.UpdateResponse[PNCounter]) extends InternalCommand
  private case class InternalGetResponse(rsp: Replicator.GetResponse[PNCounter], replyTo: ActorRef[Int]) extends InternalCommand
  private case class InternalSubscribeResponse(chg: Replicator.SubscribeResponse[PNCounter]) extends InternalCommand

  def apply(key: PNCounterKey): Behavior[Command] = Behaviors.setup[Command] { context =>
    implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress

    // adapter that turns the response messages from the replicator into our own protocol
    DistributedData.withReplicatorMessageAdapter[Command, PNCounter] { replicatorAdapter =>
      // Subscribe to changes of the given `key`.
      replicatorAdapter.subscribe(key, InternalSubscribeResponse.apply)

      def updated(cachedValue: Int): Behavior[Command] = {
        Behaviors.receiveMessage[Command] {
          case Increment =>
            replicatorAdapter.askUpdate(
              askReplyTo => Replicator.Update(key, PNCounter.empty, Replicator.WriteLocal, askReplyTo)(_ :+ 1),
              InternalUpdateResponse.apply)
            Behaviors.same

          case GetValue(replyTo) =>
            replicatorAdapter.askGet(
              askReplyTo => Replicator.Get(key, Replicator.ReadLocal, askReplyTo),
              value => InternalGetResponse(value, replyTo))
            Behaviors.same

          case GetCachedValue(replyTo) =>
            replyTo ! cachedValue
            Behaviors.same

          case RetrieveAndInc(replyTo) =>
            replyTo ! cachedValue
            replicatorAdapter.askUpdate(
              askReplyTo => Replicator.Update(key, PNCounter.empty, Replicator.WriteLocal, askReplyTo)(_ :+ 1),
              InternalUpdateResponse.apply)
            Behaviors.same

          case Decrement =>
            replicatorAdapter.askUpdate(
              askReplyTo => Replicator.Update(key, PNCounter.empty, Replicator.WriteLocal, askReplyTo)(_.decrement(1)),
              InternalUpdateResponse.apply)
            Behaviors.same

          case internal: InternalCommand =>
            internal match {
              case InternalUpdateResponse(_) => Behaviors.same // ok

              case InternalGetResponse(rsp@Replicator.GetSuccess(`key`), replyTo) =>
                val value = rsp.get(key).value.toInt
                replyTo ! value
                Behaviors.same

              case InternalGetResponse(_, _) => Behaviors.unhandled // not dealing with failures

              case InternalSubscribeResponse(chg@Replicator.Changed(`key`)) =>
                val value = chg.get(key).value.intValue
                updated(value)

              case InternalSubscribeResponse(Replicator.Deleted(_)) =>
                Behaviors.unhandled // no deletes

              case InternalSubscribeResponse(_) => // changed but wrong key
                Behaviors.unhandled

            }
        }
      }
      updated(cachedValue = 0)
    }
  }
}
