import scala.concurrent.ExecutionContext
import akka.stream.{ActorMaterializer, Materializer}
import akka.actor.ActorSystem

class Main extends App {
  implicit val actorSystem = ActorSystem("SPACE GAME 2")
  implicit val executionContext: ExecutionContext = ExecutionContext.global
  implicit val materializer: Materializer = ActorMaterializer.create(actorSystem)

  override def main(args: Array[String]): Unit = {
    val server = new WebSocketServer("localhost", 8080)
    server.run()
  }
}
