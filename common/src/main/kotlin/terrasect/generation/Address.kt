package terrasect.generation

import java.nio.ByteBuffer

object Address {
  fun serialize(buffer: ByteBuffer): String {
    val length = buffer.position()
    val sb = StringBuilder(length)

    for (i in 0 until length) {
      sb.append(buffer.get(i).toInt().toChar())
    }

    return sb.toString()
  }
}
