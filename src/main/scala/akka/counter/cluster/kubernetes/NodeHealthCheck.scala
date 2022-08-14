package akka.counter.cluster.kubernetes

import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.cluster.MemberStatus
import akka.cluster.Cluster
import org.slf4j.LoggerFactory

// Enabled in application.conf
class NodeHealthCheck(system: ActorSystem) extends (() => Future[Boolean]) {
  private val log = LoggerFactory.getLogger(getClass)
  private val cluster = Cluster(system)
  override def apply(): Future[Boolean] = {
    log.info(cluster.state.toString())
    Future.successful(cluster.selfMember.status == MemberStatus.Up)
  }
}
