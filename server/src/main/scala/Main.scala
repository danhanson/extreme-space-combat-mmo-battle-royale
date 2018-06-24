import scala.concurrent.ExecutionContext
import akka.stream.{ActorMaterializer, Materializer}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http

object Main extends App {

  override def main(args: Array[String]): Unit = {
    implicit val executionContext: ExecutionContext = ExecutionContext.global
    implicit val actorSystem: ActorSystem = ActorSystem("SPACE-GAME-2")
    implicit val materializer: Materializer = ActorMaterializer.create(actorSystem)

    val server = new WebSocketServer("localhost", 8080)
    server.run().map {
      case Http.ServerBinding(address) =>
        println(s"Game server binded to $address")
    }
  }
}
