package akka.counter.cluster.kubernetes.queue.manager
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ddata.Replicator.{ReadLocal, WriteLocal}
import akka.cluster.ddata.typed.scaladsl.Replicator.{Get, GetResponse, GetSuccess, NotFound, Update, UpdateResponse}
import akka.cluster.ddata.{LWWMap, LWWMapKey, SelfUniqueAddress}
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.counter.cluster.kubernetes.queue.manager.QueueManager.QID
import akka.counter.cluster.kubernetes.queue.manager.QueueStatus.QueueStatus

object QueueStatusRegistryReplicator {
  sealed trait Command
  final case class RegisterQID(qid: QID, status: QueueStatus) extends Command
  final case class GetQueueStatus(qid: QID, replyTo: ActorRef[QIDStatus]) extends Command
  final case class QIDStatus(qid: QID, statusOpt: Option[QueueStatus])
  final case class RemoveFromQueue(qid: QID) extends Command
  private sealed trait InternalCommand extends Command
  private case class InternalGetResponse(key: QID, replyTo: ActorRef[QIDStatus], rsp: GetResponse[LWWMap[String, QueueStatus]])
    extends InternalCommand
  private case class InternalUpdateResponse(rsp: UpdateResponse[LWWMap[QID, QueueStatus]]) extends InternalCommand

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    DistributedData.withReplicatorMessageAdapter[Command, LWWMap[String, QueueStatus]] { replicator =>
      implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress

      def dataKey(entryKey: QID): LWWMapKey[QID, QueueStatus] = {
        // Having 10000 top level entries here for replication performance
        // Ideally, we should use LWWRegister instead of LWWMap for small cluster i.e, concurrent user < 100000 users
        // see also: https://doc.akka.io/docs/akka/2.6/typed/distributed-data.html#limitations
        // This LWWMap here support to scale above 100000 concurrent users by default.
        LWWMapKey(math.abs(entryKey.hashCode % 10000).toString)
      }

      Behaviors.receiveMessage[Command] {
        case RegisterQID(qid, value) =>
          replicator.askUpdate(
            askReplyTo => Update(dataKey(qid), LWWMap.empty[QID, QueueStatus], WriteLocal, askReplyTo)(_ :+ (qid -> value)),
            InternalUpdateResponse.apply)

          Behaviors.same

        case RemoveFromQueue(qid) =>
          replicator.askUpdate(
            askReplyTo => Update(dataKey(qid), LWWMap.empty[QID, QueueStatus], WriteLocal, askReplyTo)(_.remove(node, qid)),
            InternalUpdateResponse.apply)

          Behaviors.same

        case GetQueueStatus(qid, replyTo) =>
          replicator.askGet(
            askReplyTo => Get(dataKey(qid), ReadLocal, askReplyTo),
            rsp => InternalGetResponse(qid, replyTo, rsp))

          Behaviors.same

        case InternalGetResponse(qid, replyTo, g @ GetSuccess(_)) =>
          replyTo ! QIDStatus(qid, g.dataValue.get(qid))
          Behaviors.same

        case InternalGetResponse(qid, replyTo, _: NotFound[_]) =>
          replyTo ! QIDStatus(qid, None)
          Behaviors.same

        case _: InternalGetResponse    => Behaviors.same // ok
        case _: InternalUpdateResponse => Behaviors.same // ok
      }
    }
  }
}
