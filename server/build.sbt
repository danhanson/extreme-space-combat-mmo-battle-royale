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
  "com.typesafe.akka" %% "akka-http" % "10.1.3"
)