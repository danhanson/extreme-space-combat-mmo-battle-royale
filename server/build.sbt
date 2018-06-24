name := "SpaceGame2"

version := "0.1"

scalaVersion := "2.12.6"

scalacOptions ++= Seq(
  "-Ybreak-cycles"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.5.13",
  "com.typesafe.akka" %% "akka-actor" % "2.5.13",
  "com.typesafe.akka" %% "akka-http" % "10.1.3",
  "org.ode4j" % "core" % "0.3.1"
)