package server

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ContentTypeResolver
import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.ByteString
import scala.collection.mutable
import scala.concurrent._
import java.nio.file._
import com.typesafe.scalalogging.StrictLogging

class WebServer(interface: String, port: Int)(implicit actorSystem: ActorSystem, materializer: Materializer, executionContext: ExecutionContext) extends StrictLogging {

  implicit protected def contentTypeResolver = ContentTypeResolver.Default

  private val games = mutable.Map.empty[String, Game]

  private val parseInput = Flow.fromFunction[Message, ClientInput] {
    case BinaryMessage.Strict(data) => MessageFormat.readInput(data)
    case _ => throw new UnsupportedOperationException("Must be single fragment binary message")
  }
  private val formatOutput = Flow.fromFunction[ByteString, Message](BinaryMessage(_))

  private def getGame(name: String): Game = {
    games.getOrElseUpdate(name, {
      val game = new Game(name)
      game.whenTerminated.onComplete(_ => games -= name)
      game.start()
      game
    })
  }

  val httpFlow = Route.handlerFlow {
    get {
      path("socket" / Segment / Segment) { case (room, player) =>
        headerValueByType[UpgradeToWebSocket](()) { upgrade =>
          val game = getGame(room)
          val gameFlow = game.join(player)
          complete(upgrade.handleMessages(parseInput.via(gameFlow).via(formatOutput)))
        }
      } ~
      pathPrefix("models") {
        getFromResourceDirectory("models") ~
        getFromDirectory("../models")
      } ~
      pathSingleSlash {
        getFromResource("dist/index.html") ~
        getFromFile("../client/dist/index.html")
      } ~
      getFromResourceDirectory("dist") ~
      getFromDirectory("../client/dist")
    }
  }

  private def cleanup(): Unit =
    for(game <- games.values) {
      game.terminate()
    }

  def run(): Future[Http.ServerBinding] = {
    val server = Http().bind(interface, port).to(Sink.foreach(_.handleWith(httpFlow))).run()
    server.flatMap(_.whenTerminated).onComplete { _ => cleanup() }
    server
  }
}
