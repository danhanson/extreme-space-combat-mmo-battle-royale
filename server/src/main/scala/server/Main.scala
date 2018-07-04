package server

import scala.concurrent.ExecutionContext
import akka.stream.{ActorMaterializer, Materializer}
import akka.actor.{ActorSystem, Cancellable}
import akka.http.scaladsl.Http
import java.nio.file.Paths
import scala.concurrent.duration._
import com.typesafe.scalalogging.StrictLogging
import breeze.linalg._
import org.ode4j.ode.OdeHelper

object Main extends App with StrictLogging {

  val defaultPort = 8080
  val intPattern = """(\d+)""".r
  /*Model.solveIntersection(
    Seq(DenseVector(0f, 0f, 0f), DenseVector(2f, 2f, 2f)),
    Seq(DenseVector(1f, 1f, 1f), DenseVector(3f, 3f, 3f))
  )*/

  def checkForNewline(): Boolean =
    Iterator.continually(Console.in.read())
      .takeWhile(_ => Console.in.ready())
      .exists {
        case -1 => true
        case '\r' => true
        case '\n' => true
        case _ => false
      }

  val portOption = args.headOption match {
    case Some(intPattern(number)) =>
      Some(number.toInt)
    case Some(arg) =>
      println(s"received '$arg' expected port number")
      None
    case None =>
      Some(defaultPort)
  }
  portOption.foreach { port =>

    implicit val actorSystem: ActorSystem = ActorSystem("SPACE-GAME-2")
    implicit val executionContext: ExecutionContext = ExecutionContext.global
    implicit val materializer: Materializer = ActorMaterializer.create(actorSystem)

    OdeHelper.initODE()

    val server = new WebServer("localhost", port)
    server.run().foreach {
      case binding@Http.ServerBinding(address) =>
        println(s"Game server binded to $address")
        println(s"Press Enter to shutdown server")
        do {
          Thread.sleep(500)
          logger.trace("Poll console")
        } while (!checkForNewline())
        println("Shutting down server")
        binding.terminate(5 seconds).onComplete { _ =>
          logger.debug("Terminating ActorSystem")
          actorSystem.terminate()
        }
    }
  }
}
