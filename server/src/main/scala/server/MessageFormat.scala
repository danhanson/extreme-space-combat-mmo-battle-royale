package server

import java.nio.{ByteBuffer, ByteOrder}
import java.nio.charset.StandardCharsets
import java.time.Instant
import akka.util.ByteString
import org.ode4j.math.{DQuaternionC, DVector3, DVector3C}

object MessageFormat {

  def TIME_LENTH: Int = java.lang.Long.BYTES
  val MESSAGE_TYPE_LENGTH = 1
  val ENTITY_TYPE_LENGTH = 1
  def FLOAT_BYTES: Int = java.lang.Float.BYTES

  // message types
  val NOTIFY: Byte = 0.toByte
  val UPDATE: Byte = 1.toByte

  val ENTITY_SIZE: Int = ENTITY_TYPE_LENGTH + (3 * 3 + 4) * FLOAT_BYTES // entity type + 3 vectors and 1 quaternion

  def readInput(bytes: ByteString): ClientInput = {
    val buf = bytes.asByteBuffer
    buf.order(ByteOrder.BIG_ENDIAN)
    ClientInput(
      new DVector3(buf.getFloat, buf.getFloat, buf.getFloat),
      new DVector3(buf.getFloat, buf.getFloat, buf.getFloat)
    )
  }

  private def putVector(buf: ByteBuffer, vec: DVector3C): Unit =
    for(i <- 0 to 2) {
      buf.putFloat(vec.get(i).toFloat)
    }

  private def putQuaternion(buf: ByteBuffer, quat: DQuaternionC): Unit =
    for(i <- 0 to 3) {
      buf.putFloat(quat.get(i).toFloat)
    }

  def clientUpdate(tickTime: Instant, entities: TraversableOnce[EntityData]): ByteString = {
    val buf = ByteBuffer.allocate(TIME_LENTH + MESSAGE_TYPE_LENGTH + ENTITY_SIZE * entities.size)
    buf.order(ByteOrder.BIG_ENDIAN)
    buf.putLong(tickTime.toEpochMilli)
    buf.put(UPDATE)
    for(EntityData(entity, body) <- entities) {
      buf.put(entity.id)
      putVector(buf, body.getPosition)
      putQuaternion(buf, body.getQuaternion)
      putVector(buf, body.getLinearVel)
      putVector(buf, body.getAngularVel)
    }
    ByteString.fromArrayUnsafe(buf.array())
  }

  def notification(time: Instant, text: String): ByteString = {
    val buf = ByteBuffer.allocate(TIME_LENTH + MESSAGE_TYPE_LENGTH)
    buf.order(ByteOrder.BIG_ENDIAN)
    buf.putLong(time.toEpochMilli)
    buf.put(NOTIFY)
    ByteString(buf.array()) ++ ByteString(text, StandardCharsets.UTF_8)
  }
}
