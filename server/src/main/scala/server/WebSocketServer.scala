package server

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, UpgradeToWebSocket}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri, HttpEntity}
import akka.http.scaladsl.model.HttpMethods.GET
import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import akka.util.ByteString
import akka.http.scaladsl.server.directives.ContentTypeResolver
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import java.nio.file.{Files, Path}
import com.typesafe.scalalogging.StrictLogging

class WebSocketServer(interface: String = "127.0.0.1", port: Int = 80, staticFiles: Option[Path] = None)(implicit actorSystem: ActorSystem, materializer: Materializer, executionContext: ExecutionContext) extends StrictLogging {

  def this(interface: String, port: Int, staticFiles: Path)(implicit actorSystem: ActorSystem, materializer: Materializer, executionContext: ExecutionContext) =
    this(interface, port, Some(staticFiles))

  private val rootOpt = staticFiles.map(_.normalize())
  private val SocketPath = """/socket/([^/]+)/([^/]+)""".r
  private val games = mutable.Map.empty[String, Game]
  private val contentTypeResolver = ContentTypeResolver.Default

  object FilePath {
    def unapply(path: String): Option[Path] = rootOpt.flatMap { root =>
      val resolvedPath = root.resolve(path.tail).normalize()
      if(resolvedPath.startsWith(root)) {
        val filePath = if(Files.isDirectory(resolvedPath)) {
          resolvedPath.resolve("index.html")
        } else {
          resolvedPath
        }
        if(Files.isRegularFile(filePath)) {
          Some(filePath)
        } else {
          None
        }
      } else {
        None
      }
    }
  }

  private val parseInput = Flow.fromFunction[Message, ClientInput] {
    case BinaryMessage.Strict(data) => MessageFormat.readInput(data)
    case _ => throw new UnsupportedOperationException("Must be single fragment binary message")
  }
  private val formatOutput = Flow.fromFunction[ByteString, Message](BinaryMessage(_))

  private def initializeGame(name: String): Game = {
    val game = new Game(name)
    game.start()
    game
  }

  private val httpFlow = Flow.fromFunction[HttpRequest, HttpResponse] {
    case req@HttpRequest(GET, Uri.Path(SocketPath(room, player)), _, _, _) =>
      req.header[UpgradeToWebSocket] match {
        case Some(upgrade) =>
          logger.debug(req.toString)
          val game = games.getOrElseUpdate(room, initializeGame(room))
          val gameFlow = game.join(player)
          upgrade.handleMessages(parseInput.via(gameFlow).via(formatOutput))
        case None =>
          logger.debug(req.toString)
          HttpResponse(400, entity = "Expected WebSockets request")
      }
    case req@HttpRequest(GET, Uri.Path(FilePath(file)), _, _, _) =>
      logger.debug(req.toString)
      HttpResponse(200, entity = HttpEntity.fromPath(contentTypeResolver(file.toString), file))
    case _ =>
      HttpResponse(404, entity = "Unknown Resource")
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
