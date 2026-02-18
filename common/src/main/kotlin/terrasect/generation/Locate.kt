package terrasect.generation

import terrasect.definition.Region
import java.nio.ByteBuffer

interface Locate {
  val seed: Long
  val root: Region

  fun serialize(buffer: ByteBuffer): String {
    val length = buffer.position()
    val sb = StringBuilder(length)

    for (i in 0 until length) {
      sb.append(buffer.get(i).toInt().toChar())
    }

    return sb.toString()
  }
}
