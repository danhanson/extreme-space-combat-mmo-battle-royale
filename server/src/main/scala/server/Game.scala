package server

import akka.NotUsed
import akka.actor.Cancellable
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import org.ode4j.math.{DQuaternion, DQuaternionC, DVector3, DVector3C}
import org.ode4j.ode.{DContactBuffer, DGeom, DJointGroup, OdeHelper}
import java.time.{Instant, Clock}
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import com.typesafe.scalalogging.StrictLogging

class Game(name: String)(implicit executionContext: ExecutionContext, materializer: Materializer) extends StrictLogging {

  // clock used for getting message times
  private val clock = Clock.systemUTC()

  // queue and source for global notifications
  private val (globalQueue, globalSource) = Source.queue[ByteString](5, OverflowStrategy.fail).preMaterialize()

  // geoms waiting to join the next tick
  private val pendingGeoms = mutable.Buffer.empty[DGeom]

  // interval for tick computing
  private var interval: Option[Cancellable] = None

  // length of time per tick
  val stepSize: Double = 1.0 / 30.0 // 30 ticks per second

  logger.debug("Constructing World")
  private val world = OdeHelper.createWorld()
  world.setTaskExecutor(new GameTaskExecutor(executionContext, 1))
  private val level  = OdeHelper.createHashSpace()
  private val players = mutable.Map.empty[String, Player]

  // all players have the same mass
  private val playerMass = {
    val mass = OdeHelper.createMass()
    mass.setBoxTotal(150, 1, 1, 1)
    mass
  }
  private val maxForce = 3
  private val maxTorque = 1
  private val plane = OdeHelper.createPlane(level, 0, 0, 1, 0) // place ground at z = 0

  world.setGravity(0, 0, -9.8)
  logger.debug("World Constructed")

  private def removePlayer(player: Player): Unit = {
    player.destroy()
    logger.info(s"${player.name} removed from game $name")
    globalQueue.offer(MessageFormat.notification(clock.instant(), s"${player.name} left the game"))
    players.remove(player.name)
  }

  def join(playerId: String): Flow[ClientInput, ByteString, NotUsed] = {
    players.remove(playerId).foreach { player =>
      player.destroy(new Error("Logged in somewhere else")) // XXX: reclaim player instead of respawning
    }
    logger.info(s"$playerId joining game $name")
    logger.debug(s"adding player entity $playerId to world")
    val playerGeom = OdeHelper.createBox(1, 1, 1)
    val playerBody = OdeHelper.createBody(world)
    val spaceSphere = OdeHelper.createSphere(150)
    val playerSpaceData = PlayerSpace(mutable.Buffer.empty)
    spaceSphere.setData(playerSpaceData)
    spaceSphere.setBody(playerBody)
    playerGeom.setBody(playerBody)
    playerBody.setMass(playerMass)
    pendingGeoms += playerGeom += spaceSphere
    val (playerQueue, playerSource) = Source.queue[ByteString](1, OverflowStrategy.dropHead).preMaterialize()
    val player = Player(playerId, ClientInput(DVector3.ZERO, DVector3.ZERO), playerGeom, playerSpaceData, playerQueue)
    playerSpaceData.entities += player.toEntityData
    globalQueue.offer(MessageFormat.notification(clock.instant(), s"$playerId joined the game"))
    playerGeom.setData(player)
    players(playerId) = player
    logger.debug(s"player entity $playerId added to world")
    val (done, sink) = Sink.foreach[ClientInput](player.input = _).preMaterialize()
    done.onComplete(_ => removePlayer(player))
    Flow.fromSinkAndSource(sink, playerSource.merge(globalSource))
  }

  private def handleEncounter(contactGroup: DJointGroup, o1: DGeom, o2: DGeom): Unit = {
      (o1.getData, o2.getData) match {
        case (p1: PlayerSpace, p2: Entity) => // entity exists within player space
          p1.entities += EntityData(p2, o2.getBody)
        case (e1: Entity, e2: Entity) => // 2 entities collide
          val contacts = new DContactBuffer(4)
          val contactCount = OdeHelper.collide(o1, o2, 4, contacts.getGeomBuffer)
          for(i <- 0 until contactCount) {
            val contact = contacts.get(i)
            val joint = OdeHelper.createContactJoint(world, contactGroup, contact)
            joint.attach(o1.getBody, o2.getBody)
          }
        case _ =>
          ()
      }
  }

  def clamp(max: Double)(vector: DVector3C): DVector3C =
    if(vector.length().abs > max) {
      vector.reScale(max / vector.length().abs)
    } else {
      vector
    }

  // user inputs are interpreted as invisible forces applied to player objects
  def applyInputs(): Unit = {
    for(player <- players.values) {
      player.input match {
        case ClientInput(force, torque) =>
          player.playerShape.getBody.addRelForce(clamp(maxForce)(force))
          player.playerShape.getBody.addRelTorque(clamp(maxTorque)(torque))
      }
    }
  }

  // player spaces are cleared before the next collision check
  // the only thing in each player space is the player itself,
  // which is the first entity of each space
  def clearSpaces(): Unit = {
    for(player <- players.values) {
      player.playerSpace.entities.clear()
      player.playerSpace.entities += EntityData(player, player.playerShape.getBody)
    }
  }

  // after each tick, send updates to the players
  def sendUpdates(instant: Instant): Unit = { // enqueue the messages to the clients
    for(player <- players.values) {
      player.queue.offer(MessageFormat.clientUpdate(instant, player.playerSpace.entities))
    }
  }

  def step(): Unit = {
    pendingGeoms.foreach(level.add(_))
    pendingGeoms.clear()
    logger.trace(s"$name: computing next tick")
    applyInputs()
    logger.trace(s"$name: applying player inputs")
    clearSpaces()
    val contactGroup = OdeHelper.createJointGroup()
    logger.trace(s"$name: applying step")
    level.collide(contactGroup, { (data, e1, e2) =>
      handleEncounter(data.asInstanceOf[DJointGroup], e1, e2)
    })
    world.quickStep(stepSize)
    val instant = clock.instant()
    contactGroup.empty()
    logger.trace(s"$name: next tick computed")
    logger.trace(s"$name: broadcasting world updates")
    sendUpdates(instant)
  }

  def start(): Unit = {
    logger.info(s"$name: started")
    interval = Some(materializer.schedulePeriodically(0.seconds, stepSize.seconds, () => step()))
  }

  /**
   * Stops the game, players are still connected and game can still resume
   */
  def stop(): Unit = {
    interval.foreach { ticker =>
      ticker.cancel()
      logger.info(s"$name: stopped")
      globalQueue.offer(MessageFormat.notification(clock.instant(), "Game Stopped"))
    }
    interval = None
  }

  /**
   * Terminates the server, stopping the game and disconnecting the players
   */
  def terminate(): Unit = {
    stop()
    logger.debug(s"$name: terminating")
    logger.debug(s"$name: broadcasting termination notification")
    globalQueue.offer(MessageFormat.notification(clock.instant(), "Game Terminated"))
    logger.debug(s"$name: removing all players")
    players.values.foreach(removePlayer)
    globalQueue.complete()
    logger.debug(s"$name: destroying world")
    world.destroy()
    logger.info(s"$name: terminated")
  }
}
