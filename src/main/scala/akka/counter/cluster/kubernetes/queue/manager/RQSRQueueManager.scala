package akka.counter.cluster.kubernetes.queue.manager
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.counter.cluster.kubernetes.queue.manager.QueueManager.QID
import akka.counter.cluster.kubernetes.queue.manager.QueueStatusRegistryReplicator.{QIDStatus, GetQueueStatus, RegisterQID}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future

class RQSRQueueManager(implicit val replicatedQueueStatusRegistry: ActorRef[QueueStatusRegistryReplicator.Command],
                       implicit val actorContext: ActorContext[Nothing]
                      ) extends QueueManager {

  implicit val timeout: Timeout = Timeout(500.milliseconds)

  override def allocateQID(queueNumber: Long): Future[Option[QID]] =
    replicatedQueueStatusRegistry.ask {
      ref => GetQueueStatus(hashQueueNumber(queueNumber), ref)} /*implicits*/ (timeout, actorContext.system.scheduler)
        .map {
          case QIDStatus(_, Some(_)) => None //qid already exists, in this case, we want user to explicitly retry to get the new qid instead.
          case QIDStatus(qid, None) =>
            replicatedQueueStatusRegistry ! RegisterQID(qid, QueueStatus.Waiting)
            Some(qid)
        }(actorContext.executionContext)

  private def hashQueueNumber(queueNumber: Long): QID = {
    // TODO: Use other hashing algorithm
    (queueNumber.toString + "QueueManagerHashing").hashCode().toString
  }

  override def validateQID(qid: QID): Future[Boolean] = {
    replicatedQueueStatusRegistry.ask {
      ref => GetQueueStatus(qid, ref)} /*implicits*/ (timeout, actorContext.system.scheduler)
      .map {
        case QIDStatus(_, Some(QueueStatus.Waiting)) => true
        case QIDStatus(_, Some(_)) => false // queue status is not waiting
        case QIDStatus(_, None) => false // qid does not exist
        case _ => false
      }(actorContext.executionContext)
  }
}
