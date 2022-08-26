package akka.counter.cluster.kubernetes

import akka.actor.typed.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}

// for local development, un-comment when running locally
object Node1 extends App { new DevNode(1) }
object Node2 extends App { new DevNode(2) }

class DevNode(node: Int)  {
  val config: Config = ConfigFactory.parseString(
    //TODO: Use dev config instead of ugly overriding here
    s"""akka.remote.artery.canonical.hostname = "127.0.0.$node"
      akka.management.http.hostname = "127.0.0.$node"
      akka.management.cluster.bootstrap.contact-point-discovery.discovery-method="config"
      akka.actor.allow-java-serialization = true
      akka.actor.serialize-messages = on
      akka.actor.serialize-creators = on
      """).withFallback(ConfigFactory.load())
  ActorSystem[Nothing](Booty.boot(8080 + node), "appka", config)
}
