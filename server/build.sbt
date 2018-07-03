name := "SpaceGame2"

version := "0.1"

scalaVersion := "2.12.6"

scalacOptions ++= Seq(
  "-Ybreak-cycles",
  "-feature",
  "-language:postfixOps",
  "-deprecation"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.5.13",
  "com.typesafe.akka" %% "akka-actor" % "2.5.13",
  "com.typesafe.akka" %% "akka-http" % "10.1.3",
  "org.scalanlp" %% "breeze" % "1.0-RC2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
)