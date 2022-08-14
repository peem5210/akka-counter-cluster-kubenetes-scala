package akka.counter.cluster.kubernetes

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent
import akka.cluster.typed.{Cluster, ClusterSingleton, SingletonActor, Subscribe}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.javadsl.AkkaManagement
import GlobalCounter.{GetValue, Increment}
import akka.util.Timeout
import akka.actor.typed.scaladsl.adapter._
import akka.{actor => classic}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

// for k8s deployment
object Main extends App {
  ActorSystem[Nothing](MainBehavior.behaviors(8080), "appka")
}

object MainBehavior {
  def behaviors(port: Int = 0): Behavior[Nothing] =
  Behaviors.setup[Nothing] { context =>
    implicit val classicSystem: classic.ActorSystem = context.system.toClassic
    implicit val ec: ExecutionContextExecutor = context.system.executionContext

    val cluster = Cluster(context.system)
    val singletonManager = ClusterSingleton(context.system)
    val counterActor: ActorRef[GlobalCounter.Command] = singletonManager.init(
      SingletonActor(Behaviors.supervise(GlobalCounter()).onFailure[Exception](SupervisorStrategy.restart), "GlobalCounter")
    )

    context.log.info(s"Started [${context.system}], address = ${cluster.selfMember.address}, role = ${cluster.selfMember.address}")
    Http().newServerAt("0.0.0.0", port).bind(
      concat(path("get-count") {
        get {
          onComplete(counterActor.ask(ref => GetValue(ref))(Timeout(3.seconds), context.system.scheduler)) {
            case Success(value) => complete(StatusCodes.OK, value.toString)
            case Failure(ex) => complete(StatusCodes.InternalServerError, ex.getMessage)
          }
        }
      }, path("increment") {
        get {
          counterActor ! Increment
          complete("send increment")
        }
      })
    )

    // Create an actor that handles cluster domain events
    val listener = context.spawn(Behaviors.receive[ClusterEvent.MemberEvent]((ctx, event) => {
      ctx.log.info("MemberEvent: {}", event)
      Behaviors.same
    }), "listener")

    Cluster(context.system).subscriptions ! Subscribe(listener, classOf[ClusterEvent.MemberEvent])

    AkkaManagement.get(classicSystem).start()
    ClusterBootstrap.get(classicSystem).start()
    Behaviors.empty
  }
}
