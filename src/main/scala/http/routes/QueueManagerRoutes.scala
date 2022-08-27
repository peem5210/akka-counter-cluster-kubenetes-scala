package http.routes

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.counter.cluster.kubernetes.counter.{GlobalCounter, GlobalCounterReplicator}
import akka.counter.cluster.kubernetes.counter.GlobalCounterReplicator.{Decrement, RetrieveAndInc}
import akka.counter.cluster.kubernetes.queue.manager.QueueManager
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, get, onComplete, parameters, path}
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

class QueueManagerRoutes(implicit val ctx: ActorContext[Nothing],
                         implicit val counterActor: ActorRef[GlobalCounter.Command],
                         implicit val queueManager: QueueManager,
                         implicit val replicatedCounterActor: ActorRef[GlobalCounterReplicator.Command]
                      ) extends RouteClass {
  override def getRoute: Route =
    Directives.concat(path("allocate-qid") {
      get {
        onComplete(
          replicatedCounterActor.ask { ref => RetrieveAndInc(ref) }
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
    }, path("deallocate-qid") {
      get {
        replicatedCounterActor ! Decrement
        complete(StatusCodes.OK, "Successful")
      }
    }, path("validate-qid") {
      get {
        parameters("qid") { qid =>
          onComplete(queueManager.validateQID(qid)) {
            case Success(value) =>
              complete(StatusCodes.OK, value.toString)
            case Failure(ex) =>
              complete(StatusCodes.InternalServerError, ex.getMessage)
          }
        }
      }
    })
}
