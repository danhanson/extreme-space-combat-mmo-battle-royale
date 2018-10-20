package server

import akka.stream.scaladsl.SourceQueueWithComplete
import akka.util.ByteString
import org.ode4j.ode.DGeom

import scala.collection.mutable

case class Player(
    name: String, // unique id for each player
    var input: ClientInput, // commands provided by client
    playerShape: DGeom, // shape representing the player, must have a body
    playerSpace: PlayerSpace, // nearby objects player sees and interacts with
    queue: SourceQueueWithComplete[ByteString] // enqueues updates sent to client
) extends Entity {

  override val id: Byte = 0.toByte

  /**
    * Removes player from game and closes websocket, providing an optional throwable if caused by exception.
    * Player should not be used after destroy is called.
    *
    * @param excOpt
    */
  def destroy(excOpt: Option[Throwable]): Unit = {
    Option(playerShape.getBody).foreach(_.destroy())
    excOpt.fold(queue.complete())(queue.fail)
  }

  /**
    * Player destroyed normally
    */
  def destroy(): Unit = destroy(None)

  /**
    * Player destroyed as a result of an exception, failure is transmitted to client
    * @param exc
    */
  def destroy(exc: Throwable): Unit = destroy(Some(exc))

  def toEntityData: EntityData =
    EntityData(this, playerShape.getBody)
}
