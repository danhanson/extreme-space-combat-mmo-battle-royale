name := "SpaceGame2"

version := "0.1"

scalaVersion := "2.12.7"

scalacOptions ++= Seq(
  "-Ybreak-cycles",
  "-feature",
  "-language:postfixOps",
  "-deprecation"
)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
  "com.typesafe.akka" %% "akka-stream" % "2.5.17",
  "com.typesafe.akka" %% "akka-actor" % "2.5.17",
  "com.typesafe.akka" %% "akka-http" % "10.1.5",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
)