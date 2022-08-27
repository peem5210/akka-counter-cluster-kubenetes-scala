package http.routes

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.counter.cluster.kubernetes.counter.GlobalCounter
import GlobalCounter.Increment
import akka.http.scaladsl.server.Directives.{complete, get, path}
import akka.http.scaladsl.server.Route

class IncrementCounterRoute(implicit val ctx: ActorContext[Nothing], implicit val counterActor: ActorRef[GlobalCounter.Command]) extends RouteClass {
  override def getRoute: Route =
    path("increment") {
      get {
        counterActor ! Increment
        complete("send increment")
      }
    }
}
