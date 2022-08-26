package http.routes

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.counter.cluster.kubernetes.GlobalCounter
import akka.counter.cluster.kubernetes.GlobalCounter.RetrieveAndInc
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, get, onComplete, path}
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout
import queue.manager.QueueManager

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

class AllocateQidRoute(implicit val ctx: ActorContext[Nothing],
                       implicit val counterActor: ActorRef[GlobalCounter.Command],
                       implicit val queueManager: QueueManager
                      ) extends RouteClass {
  override def getRoute: Route =
    Directives.concat(path("allocate-qid") {
      get {
        onComplete(
          counterActor.ask { ref => RetrieveAndInc(ref) }
          /* implicits */
          (Timeout(500.milliseconds), ctx.system.scheduler)
        ){
            // value can be duplicated across multiple request here
            // as actor operation is non-blocking asynchronous
            // we can get stale state from the global counter
            // when actor 'mailbox' is not empty
            case Success(value) =>
              complete(StatusCodes.OK, queueManager.allocateQID(value))
            case Failure(ex) =>
              complete(StatusCodes.InternalServerError, ex.getMessage)
        }
      }
    })
}
