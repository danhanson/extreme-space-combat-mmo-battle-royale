package server

import akka.{NotUsed, Done}
import akka.actor.Cancellable
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import org.ode4j.math._
import org.ode4j.ode._
import java.time._
import scala.collection.mutable
import scala.concurrent._
import scala.concurrent.duration._
import com.typesafe.scalalogging.StrictLogging

class Game(name: String)(implicit executionContext: ExecutionContext, materializer: Materializer) extends StrictLogging {

  private val terminationPromise = Promise[Done]()

  val GROUND = 1
  val PLAYER = 2
  val PLAYER_SPACE = 4

  def whenTerminated: Future[Done] = terminationPromise.future

  // clock used for getting message times
  private val clock = Clock.systemUTC()

  private val playerModel = Model.load("player")

  // queue and source for global notifications
  private val (globalQueue, globalSource) = Source.queue[ByteString](5, OverflowStrategy.fail).preMaterialize()

  // tasks waiting to run for the next tick
  private val pendingTasks = mutable.Buffer.empty[() => Any]

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
  private val maxForce = 400
  private val maxTorque = 100
  // private val plane = OdeHelper.createPlane(level, 0, 0, 1, 0) // place ground at z = 0
  // plane.setCategoryBits(GROUND)

  world.setGravity(0, 0, 0)
  logger.debug("World Constructed")

  private def removePlayer(player: Player, termination: Boolean = false): Unit = {
    if(!players.contains(player.name)) {
      logger.debug(s"$name: ${player.name} already removed")
      return
    }
    if(termination) {
      player.destroy()
    } else {
      pendingTasks += { () => player.destroy() }
    }
    logger.info(s"$name: ${player.name} removed from game")
    globalQueue.offer(MessageFormat.notification(clock.instant(), s"${player.name} left the game"))
    players.remove(player.name)
    if(!termination && players.size == 0) {
      logger.info(s"$name: all players left game, terminating")
      terminate()
    }
  }

  def join(playerId: String): Flow[ClientInput, ByteString, NotUsed] = {
    players.remove(playerId).foreach { player =>
      player.destroy(new Error("Logged in somewhere else")) // XXX: reclaim player instead of respawning
    }
    logger.info(s"$playerId joining game $name")
    logger.debug(s"adding player entity $playerId to world")
    val playerGeom = playerModel.geom()
    val playerBody = OdeHelper.createBody(world)
    val spaceSphere = OdeHelper.createSphere(150)
    val playerSpaceData = PlayerSpace(mutable.Buffer.empty)
    spaceSphere.setCategoryBits(PLAYER_SPACE)
    spaceSphere.setCollideBits(PLAYER)
    spaceSphere.setData(playerSpaceData)
    spaceSphere.setBody(playerBody)
    playerGeom.setCategoryBits(PLAYER)
    playerGeom.setCollideBits(PLAYER | GROUND)
    playerGeom.setBody(playerBody)
    playerBody.setMass(playerMass)
    playerBody.setAngularDampingThreshold(0)
    playerBody.setAngularDamping(0.15)
    playerBody.setLinearDampingThreshold(2000)
    playerBody.setLinearDamping(1)
    pendingTasks += { () => level.add(playerGeom) } += { () => level.add(spaceSphere) }
    val (playerQueue, playerSource) = Source.queue[ByteString](1, OverflowStrategy.dropHead).preMaterialize()
    val player = Player(playerId, ClientInput(DVector3.ZERO, DVector3.ZERO), playerGeom, playerSpaceData, playerQueue)
    globalQueue.offer(MessageFormat.notification(clock.instant(), s"$playerId joined the game"))
    playerGeom.setData(player)
    players(playerId) = player
    logger.debug(s"player entity $playerId added to world")
    val (done, sink) = Sink.foreach[ClientInput] { input =>
      logger.debug(s"$name: $playerId: input: $input at: ${playerBody.getPosition()}")
      player.input = input
    }.preMaterialize()
    done.onComplete(_ => removePlayer(player))
    Flow.fromSinkAndSource(sink, playerSource.merge(globalSource))
  }

  private def handleEncounter(contactGroup: DJointGroup, o1: DGeom, o2: DGeom): Unit = {
      (o1.getData, o2.getData) match {
        case (e: Entity, space: PlayerSpace) => // entity exists within player space
          space.entities += EntityData(e, o1.getBody)
        case (e1: Entity, e2: Entity) => // 2 entities collide, TODO: send player collisions as updates to server
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
          logger.trace(s"input: $force, $torque")
          player.playerShape.getBody.addRelForce(clamp(maxForce)(force.reScale(4)))
          player.playerShape.getBody.addRelTorque(clamp(maxTorque)(torque))
      }
    }
  }

  // player spaces are cleared before the next collision check
  // the only thing in each player space is the player itself,
  // which is the first entity of each space
  def resetSpaces(): Unit = {
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
    pendingTasks.foreach(_.apply())
    pendingTasks.clear()
    logger.trace(s"$name: computing next tick")
    applyInputs()
    logger.trace(s"$name: applying player inputs")
    val contactGroup = OdeHelper.createJointGroup()
    logger.trace(s"$name: applying step")
    resetSpaces()
    level.collide(contactGroup, { (data, e1, e2) =>
      handleEncounter(data.asInstanceOf[DJointGroup], e1, e2)
    })
    world.step(stepSize)
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
    pendingTasks.foreach(_.apply())
    pendingTasks.clear()
    // wait for possible last tick to finish
    materializer.scheduleOnce(stepSize.seconds, () => {
      logger.debug(s"$name: terminating")
      logger.debug(s"$name: broadcasting termination notification")
      globalQueue.offer(MessageFormat.notification(clock.instant(), "Game Terminated"))
      logger.debug(s"$name: removing all players")
      players.values.foreach(removePlayer(_, true))
      globalQueue.complete()
      logger.debug(s"$name: destroying world")
      world.destroy()
      logger.info(s"$name: terminated")
      terminationPromise.success(Done)
    })
  }
}
