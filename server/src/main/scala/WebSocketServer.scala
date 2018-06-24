import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, UpgradeToWebSocket}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.model.HttpMethods.GET
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import akka.util.ByteString

import scala.collection.mutable
import scala.concurrent.ExecutionContext

class WebSocketServer(interface: String, port: Int)(implicit actorSystem: ActorSystem, materializer: Materializer, executionContext: ExecutionContext) {

  private val Path = """(\w+)/(\w+)""".r
  private val games = mutable.Map.empty[String, Game]

  private val parseInput = Flow.fromFunction[Message, ClientInput] {
    case BinaryMessage.Strict(data) => MessageFormat.readInput(data)
    case BinaryMessage.Streamed(_) => throw new UnsupportedOperationException("No multi-fragment messages allowed") // impossible with js WebSocket api
  }
  private val formatOutput = Flow.fromFunction[ByteString, Message](BinaryMessage(_))

  private def initializeGame(name: String): Game = {
    val game = new Game(name)
    game.start()
    game
  }

  private val socketFlow = Flow.fromFunction[HttpRequest, HttpResponse] {
    case req@HttpRequest(GET, Uri.Path(Path(room, player)), _, _, _) =>
      req.header[UpgradeToWebSocket] match {
        case Some(upgrade) =>
          val game = games.getOrElseUpdate(room, initializeGame(room))
          val gameFlow = game.join(player)
          upgrade.handleMessages(parseInput.via(gameFlow).via(formatOutput))
        case None =>
          HttpResponse(400, entity = "Expected WebSocket request")
      }
    case _ =>
      HttpResponse(404, entity = "Unknown Resource")
  }

  def run() {
    Http().bind(interface, port).runWith(Sink.foreach(_.handleWith(socketFlow)))
  }
}
