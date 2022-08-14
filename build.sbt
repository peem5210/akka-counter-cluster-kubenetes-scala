organization in ThisBuild := "com.lightbend"

name := "akka-counter-cluster-kubernetes"

// make version compatible with docker for publishing
ThisBuild / dynverSeparator := "-"

scalaVersion := Versions.scalaVersion
scalacOptions := Seq("-feature", "-unchecked", "-deprecation", "-encoding", "utf8")
classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.AllLibraryJars
fork in run := true
Compile / run / fork := true
mainClass in (Compile, run) := Some("akka.sample.cluster.kubernetes.Main")

enablePlugins(JavaServerAppPackaging, DockerPlugin)
dockerExposedPorts := Seq(8080, 8558, 25520)
dockerUpdateLatest := true
dockerUsername := sys.props.get("docker.username")
dockerRepository := sys.props.get("docker.registry")
dockerBaseImage := "adoptopenjdk:8-jre-hotspot"

libraryDependencies ++= {
  Seq(
    "ch.qos.logback" % "logback-classic" % Versions.logBackVersion,
    "com.typesafe.akka" %% "akka-http" % Versions.akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % Versions.akkaHttpVersion,
    "com.typesafe.akka" %% "akka-cluster-typed" % Versions.akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % Versions.akkaVersion,
    "com.typesafe.akka" %% "akka-stream-typed" % Versions.akkaVersion,
    "com.typesafe.akka" %% "akka-discovery" % Versions.akkaVersion,
    "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % Versions.akkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % Versions.akkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-http" % Versions.akkaManagementVersion,
    "com.typesafe.akka" %% "akka-testkit" % Versions.akkaVersion % "test",
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % Versions.akkaVersion % Test,
    "com.typesafe.akka" %% "akka-http-testkit" % Versions.akkaHttpVersion % Test,
    "com.typesafe.akka" %% "akka-testkit" % Versions.akkaVersion % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % Versions.akkaVersion % Test)
}
