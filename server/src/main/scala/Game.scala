import akka.NotUsed
import akka.actor.Cancellable
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import org.ode4j.math.{DQuaternion, DQuaternionC, DVector3, DVector3C}
import org.ode4j.ode.{DContactBuffer, DGeom, DJointGroup, OdeHelper}

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class Game(name: String)(implicit executionContext: ExecutionContext, materializer: Materializer) {

  private val (globalQueue, globalSource) = Source.queue[ByteString](5, OverflowStrategy.fail).preMaterialize()
  private var interval: Option[Cancellable] = None
  private var tickTime: Option[Long] = None
  val stepSize: Double = 1.0 / 30.0 // 30 ticks per second

  private val world = OdeHelper.createWorld()
  private val level  = OdeHelper.createHashSpace()
  private val players = mutable.Map.empty[String, Player]
  private val playerMass = {
    val mass = OdeHelper.createMass()
    mass.setMass(150)
    mass
  }
  private val maxForce = 3
  private val maxTorque = 1
  private val plane = OdeHelper.createPlane(level, 0, 0, 1, 0) // place ground at z = 0

  world.setGravity(0, 0, -9.8)

  private def removePlayer(player: Player): Unit = {
    player.destroy()
    globalQueue.offer(MessageFormat.notification(s"${player.name} left the game"))
  }

  def join(playerId: String): Flow[ClientInput, ByteString, NotUsed] = {
    players.remove(playerId).foreach { player =>
      player.destroy(new Error("Logged in somewhere else"))
    }
    val playerGeo = OdeHelper.createBox(1, 1, 1)
    val playerBody = OdeHelper.createBody(world)
    val playerSpace = OdeHelper.createSimpleSpace(level)
    val spaceSphere = OdeHelper.createSphere(playerSpace, 150)
    val playerSpaceData = PlayerSpace(mutable.Buffer.empty)
    spaceSphere.setData(playerSpaceData)
    playerSpace.setBody(playerBody)
    playerGeo.setBody(playerBody)
    playerBody.setMass(playerMass)
    level.add(playerGeo)
    playerBody.setLinearDampingThreshold(10) // implicitly sets max speed
    val (playerQueue, playerSource) = Source.queue[ByteString](1, OverflowStrategy.dropHead).preMaterialize()
    val player = Player(playerId, ClientInput(DVector3.ZERO, DVector3.ZERO), playerGeo, playerSpaceData, playerQueue)
    globalQueue.offer(MessageFormat.notification(s"$playerId joined the game"))
    playerGeo.setData(player)
    players(playerId) = player
    val (done, sink) = Sink.foreach[ClientInput](player.input = _).preMaterialize()
    done.onComplete(_ => player.destroy())
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
  def sendUpdates(): Unit = { // enqueue the messages to the clients
    for(player <- players.values) {
      player.queue.offer(MessageFormat.clientUpdate(player.playerSpace.entities))
    }
  }

  def step(): Unit = { // rewrite this to do collisions and updates all at once
    applyInputs()
    clearSpaces()
    val contactGroup = OdeHelper.createJointGroup()
    level.collide(contactGroup, { (data, e1, e2) =>
      handleEncounter(data.asInstanceOf[DJointGroup], e1, e2)
    })
    world.quickStep(stepSize)
    contactGroup.empty()
    sendUpdates()
  }

  def start(): Unit = {
    interval = Some(materializer.schedulePeriodically(0.seconds, stepSize.seconds, () => step()))
  }

  def stop(): Unit = {
    interval.map(_.cancel())
  }
}
