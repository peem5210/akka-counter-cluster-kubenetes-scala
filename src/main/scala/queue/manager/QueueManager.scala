package queue.manager

import queue.manager.QueueManager.QID
import queue.manager.QueueStatus.QueueStatus

import scala.collection.mutable

object QueueManager {
  // Queue ID as String
  type QID = String
}
trait QueueManager {
  def allocateQID(queueNumber: Long): QID
  def validateQID(qid: QID): Boolean

}

/**
 * Implementation of [[QueueManager]]. 'PerNode' means that it works per node, can't be used in a cluster
 * *** for dev environment, should not be used in production in any way ***
 */
class MapBasedPerNodeQueueManager extends QueueManager {

  private val mem: mutable.Map[QID, QueueStatus] = mutable.Map.empty.withDefault(_ => QueueStatus.Unknown)
  private val dupMem: mutable.Map[QID, Int] = mutable.Map.empty.withDefault(_ => 0)

  def allocateQID(queueNumber: Long): QID = {
    processQID(queueNumber.toString)
  }

  private def processQID(qid: QID): QID = {
    if (mem.contains(qid)) { // got duplicated qid
      val curDup = dupMem.getOrElse(qid, 0)
      val newQID = s"$qid-$curDup" // append the duplication index to the end of the qid
      mem.addOne(newQID, QueueStatus.Waiting)
      dupMem.update(qid, curDup + 1)
      newQID

    } else { // no qid duplication
      mem.addOne(qid, QueueStatus.Waiting)
      qid
    }
  }

  def validateQID(qid: QID): Boolean = {
    mem.get(qid).contains(QueueStatus.Waiting)
  }
}
