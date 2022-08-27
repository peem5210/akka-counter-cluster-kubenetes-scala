package akka.counter.cluster.kubernetes

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent
import akka.cluster.typed.{Cluster, ClusterSingleton, SingletonActor, Subscribe}
import akka.http.scaladsl.Http
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.javadsl.AkkaManagement
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.ddata.{GCounterKey, PNCounterKey, SelfUniqueAddress}
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.counter.cluster.kubernetes.counter.{GlobalCounter, GlobalCounterReplicator}
import akka.counter.cluster.kubernetes.queue.manager.{MapBasedPerNodeQueueManager, RQSRQueueManager, QueueStatusRegistryReplicator}
import akka.{actor => classic}
import http.RouteBindings

import scala.concurrent.ExecutionContextExecutor

// for k8s deployment
object Main extends App {
  ActorSystem[Nothing](Booty.boot(8080), "appka")
}

object Booty {
  private val CIDR_RANGE = "0.0.0.0"
  def boot(port: Int = 0): Behavior[Nothing] = Behaviors.setup[Nothing] { implicit context =>
    //TODO: check if we can use Actor system from akka typed instead of classic Actor system
    implicit val classicSystem: classic.ActorSystem = context.system.toClassic
    implicit val ec: ExecutionContextExecutor = context.system.executionContext

    val cluster = Cluster(context.system)
    val singletonManager = ClusterSingleton(context.system)
    // * NOT USED *
    // TODO: Delete singleton counter from the project :)
    // Retrieve or create(if not exist) counter singleton actor proxy
    val counterActorProxy: ActorRef[GlobalCounter.Command] = singletonManager.init(
      SingletonActor(Behaviors.supervise(GlobalCounter()).onFailure[Exception](SupervisorStrategy.restart), GlobalCounter.name)
    )

    // Using replicator instead of singleton actor will be much more fault-tolerance and way easier to handle node downing situation.
    // in trade for little performance loss, which is not a big concern as increment/decrement is a small cost operation.
    val replicatedGlobalCounter = context.spawn(GlobalCounterReplicator(PNCounterKey("ReplicatedGlobalCounter")), "ReplicatedGlobalCounter")
    val replicatedQueueStatusRegistry = context.spawn(QueueStatusRegistryReplicator(), "ReplicatedQueueStatusRegistry")

    context.log.info(s"Started [${context.system}], address = ${cluster.selfMember.address}, role = ${cluster.selfMember.address}")

    // Initializations
    val queueManager = new RQSRQueueManager()(
      replicatedQueueStatusRegistry,
      context
    )
    val routeBindings = new RouteBindings()(
      // project motto 1. "make implicits explicit"
      context,
      counterActorProxy,
      queueManager,
      replicatedGlobalCounter
    )


    // Create http server and bind the routes to it.
    // TODO: Check if graceful termination is needed here?
    val _ = Http().newServerAt(CIDR_RANGE, port)
      .bind(routeBindings.getRoute)

    // Create an actor that logs cluster domain events for debugging purposes
    val listener = context.spawn(Behaviors.receive[ClusterEvent.MemberEvent]((ctx, event) => {
      ctx.log.info("MemberEvent: {}", event)
      Behaviors.same
    }), "listener")

    // Subscribe to the state changes and log to the console for debugging purposes
    Cluster(context.system).subscriptions ! Subscribe(listener, classOf[ClusterEvent.MemberEvent])


    // Start the actual akka cluster node and akka cluster management
    AkkaManagement.get(classicSystem).start()
    ClusterBootstrap.get(classicSystem).start()
    Behaviors.empty
  }
}
