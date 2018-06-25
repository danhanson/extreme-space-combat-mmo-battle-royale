package server

import scala.concurrent.ExecutionContext
import akka.stream.{ActorMaterializer, Materializer}
import akka.actor.{ActorSystem, Cancellable}
import akka.http.scaladsl.Http
import java.nio.file.Paths
import scala.concurrent.duration._

object Main extends App {
  implicit val actorSystem: ActorSystem = ActorSystem("SPACE-GAME-2")

  def checkForNewline(): Boolean =
    Iterator.continually(Console.in.read())
      .takeWhile(_ => Console.in.ready())
      .exists {
        case -1 => true
        case '\r' => true
        case '\n' => true
        case _ => false
      }

  def pollConsole(binding: Http.ServerBinding, poll: Cancellable): Unit = {
    if(checkForNewline()) {
      poll.cancel()
      println("Shutting down server")
      binding.terminate(5 seconds).onComplete { _ =>
        actorSystem.terminate()
      }
    }
  }

  implicit val executionContext: ExecutionContext = ExecutionContext.global
  implicit val materializer: Materializer = ActorMaterializer.create(actorSystem)
  val staticPath = Paths.get("../client/dist")
  val server = new WebSocketServer("localhost", 8080, staticPath)

  server.run().foreach {
    case binding@Http.ServerBinding(address) =>
      println(s"Game server binded to $address")
      println(s"Serving files from ${staticPath.normalize().toAbsolutePath()}")
      println(s"Press Enter to shutdown server")
      var poll = Cancellable.alreadyCancelled
      poll = materializer.schedulePeriodically(0 seconds, 1 second, () => pollConsole(binding, poll))
  }
}
