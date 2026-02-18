package terrasect.generation

import org.junit.jupiter.api.Test
import terrasect.definition.Region
import terrasect.definition.Strategy
import terrasect.definition.StrategyId
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class LocateTest {
  private val locate =
      object : Locate {
        override val seed: Long = 0
        override val root: Region = Region.empty("root")
      }

  @Test
  fun `should serialize structured traversal id in compact form`() {
    val buffer = ByteBuffer.allocate(256)

    buffer.putChar(Strategy.ID)
    buffer.put(StrategyId.HEX.value)
    buffer.putInt(0)
    buffer.putInt(0)
    buffer.putChar(Strategy.REGION)
    buffer.put("cell".toByteArray(StandardCharsets.UTF_8))
    buffer.putChar(Strategy.SEPARATOR)

    buffer.putChar(Strategy.ID)
    buffer.put(StrategyId.VORONOI.value)
    buffer.putInt(123456)
    buffer.putInt(3)
    buffer.putChar(Strategy.REGION)
    buffer.put("voronoi3".toByteArray(StandardCharsets.UTF_8))
    buffer.putChar(Strategy.SEPARATOR)

    buffer.putChar(Strategy.ID)
    buffer.put(StrategyId.SURROUND.value)
    buffer.putInt(16)
    buffer.putInt(8)
    buffer.putChar(Strategy.REGION)
    buffer.put("surround".toByteArray(StandardCharsets.UTF_8))
    buffer.putChar(Strategy.SEPARATOR)

    val str = locate.serialize(buffer)
  }

  @Test
  fun `should escape unknown bytes`() {
    val bytes =
        byteArrayOf(
            'A'.code.toByte(),
            ' '.code.toByte(),
            '%'.code.toByte(),
            '~'.code.toByte(),
            0x7F.toByte(),
            0x00.toByte(),
            0x1F.toByte(),
            0x80.toByte(),
            0xFF.toByte(),
        )
    val buffer = ByteBuffer.allocate(bytes.size).put(bytes)

    val str = locate.serialize(buffer)
  }
}
