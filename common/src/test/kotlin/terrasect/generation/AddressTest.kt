package terrasect.generation

import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class AddressTest {

  @Test
  fun `should serialize structured traversal id in compact form`() {
    val buffer = ByteBuffer.allocate(256)

    buffer.put('#'.code.toByte())
    buffer.put(1.toByte())
    buffer.putInt(1)
    buffer.putInt(20)
    buffer.put('.'.code.toByte())
    buffer.put("cell".toByteArray())
    buffer.put(' '.code.toByte())

    buffer.put('#'.code.toByte())
    buffer.put(2.toByte())
    buffer.putInt(123456)
    buffer.putInt(3)
    buffer.put('.'.code.toByte())
    buffer.put("voronoi3".toByteArray())
    buffer.put(' '.code.toByte())

    buffer.put('#'.code.toByte())
    buffer.put(3.toByte())
    buffer.putInt(16)
    buffer.putInt(8)
    buffer.put('.'.code.toByte())
    buffer.put("surround".toByteArray())
    buffer.put(' '.code.toByte())

    val str = Address.serialize(buffer)
  }
}
