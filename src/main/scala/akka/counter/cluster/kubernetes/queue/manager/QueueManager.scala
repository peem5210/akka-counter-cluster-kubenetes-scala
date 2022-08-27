package akka.counter.cluster.kubernetes.queue.manager

import QueueManager.QID
import QueueStatus.QueueStatus

import scala.collection.mutable
import scala.concurrent.Future

object QueueManager {
  // Queue ID 1-1 hash string mapping per user/session
  type QID = String
}
trait QueueManager {
  def allocateQID(queueNumber: Long): Future[Option[QID]]
  def validateQID(qid: QID): Future[Boolean]

}

/**
 * Implementation of [[QueueManager]]. 'PerNode' means that it works per node, can't be used in a cluster
 * *** for dev/testing environment, should not be used in production in any way ***
 */
class MapBasedPerNodeQueueManager extends QueueManager {

  private val mem: mutable.Map[QID, QueueStatus] = mutable.Map.empty.withDefault(_ => QueueStatus.Unknown)

  def allocateQID(queueNumber: Long): Future[Option[QID]] = {
    processQID(queueNumber.toString)
  }

  private def processQID(qid: QID): Future[Option[QID]] = {
    if (mem.contains(qid)) { // got duplicated qid
      Future.successful(None)

    } else { // no qid duplication
      mem.addOne(qid, QueueStatus.Waiting)
      Future.successful(Some(qid))
    }
  }

  def validateQID(qid: QID): Future[Boolean] = {
    Future.successful(mem.get(qid).contains(QueueStatus.Waiting))
  }
}
