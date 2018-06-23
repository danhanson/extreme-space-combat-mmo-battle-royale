import java.nio.{ByteBuffer, ByteOrder}
import java.nio.charset.StandardCharsets

import akka.util.ByteString
import org.ode4j.math.{DQuaternionC, DVector3, DVector3C}

object MessageFormat {

  def TIME_LENTH: Int = java.lang.Long.BYTES
  val MESSAGE_TYPE_LENGTH = 1
  val ENTITY_TYPE_LENGTH = 1
  def DOUBLE_BYTES: Int = java.lang.Double.BYTES

  // message types
  val NOTIFY: Byte = 0.toByte
  val UPDATE: Byte = 1.toByte


  val ENTITY_SIZE: Int = ENTITY_TYPE_LENGTH + (3 * 3 + 4) * DOUBLE_BYTES // entity type + 3 vectors and 1 quaternion

  def readInput(bytes: ByteString): ClientInput = {
    val buf = bytes.asByteBuffer
    buf.order(ByteOrder.BIG_ENDIAN)
    ClientInput(
      new DVector3(buf.getDouble, buf.getDouble, buf.getDouble),
      new DVector3(buf.getDouble, buf.getDouble, buf.getDouble)
    )
  }

  private def putVector(buf: ByteBuffer, vec: DVector3C): Unit =
    for(i <- 0 to 2) {
      buf.putDouble(vec.get(i))
    }

  private def putQuaternion(buf: ByteBuffer, quat: DQuaternionC): Unit =
    for(i <- 0 to 3) {
      buf.putDouble(quat.get(i))
    }

  def clientUpdate(tickTime: Long, entities: TraversableOnce[EntityData]): ByteString = {
    val buf = ByteBuffer.allocate(TIME_LENTH + MESSAGE_TYPE_LENGTH + ENTITY_SIZE * entities.size)
    buf.order(ByteOrder.BIG_ENDIAN)
    buf.putLong(tickTime)
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

  def notification(time: Long, text: String): ByteString = {
    val buf = ByteBuffer.allocate(TIME_LENTH + MESSAGE_TYPE_LENGTH)
    buf.order(ByteOrder.BIG_ENDIAN)
    buf.putLong(time)
    buf.put(NOTIFY)
    ByteString(buf.array()) ++ ByteString(text, StandardCharsets.UTF_8)
  }
}
