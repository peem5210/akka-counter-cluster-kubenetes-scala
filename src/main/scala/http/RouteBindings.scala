package http

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.ActorRef
import akka.counter.cluster.kubernetes.counter.{GlobalCounter, GlobalCounterReplicator}
import akka.counter.cluster.kubernetes.queue.manager.QueueManager
import akka.http.scaladsl.server.Directives.concat
import akka.http.scaladsl.server.Route
import http.routes.{IncrementCounterRoute, QueueManagerRoutes, RouteClass, WSRoute}


class RouteBindings(implicit val ctx: ActorContext[Nothing],
                    implicit val counterActor: ActorRef[GlobalCounter.Command],
                    implicit val queueManager: QueueManager,
                    implicit val replicatedCounterActor: ActorRef[GlobalCounterReplicator.Command]
                   ) extends RouteClass {
  //project motto 2. "Why use complex DI library, when all you need is implicits?"
  //project motto 3. "Implicit when trivial, otherwise explicit"
  override def getRoute: Route = {
    concat(Seq(
      new QueueManagerRoutes,
        new IncrementCounterRoute,
        new WSRoute
      ).map(_.getRoute):_*)
  }
}
