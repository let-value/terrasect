package terrasect.generation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import terrasect.generation.TraverserTest.Companion.registry
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

    val actual = Address.serialize(buffer)
    assertEquals(
        "wyv8SyjznNZnLVeABWfKHrHbnWN5LGXSgtN5Qgh6CifvHBY7rSX3HhRJHbfvc2rTvzmkAiBmjsHh",
        actual,
    )
  }

  @Test
  fun `should serialize traverse step`() {
    val root = registry.buildTree("hex")
    val traverse = Traverser(1234L, root)

    val step = traverse.iterate(0, 0)
    assertEquals("", Address.serialize(step.id))

    step.next()
    assertEquals("8C6SYbeQ2LQTpB", Address.serialize(step.id))

    step.next()
    assertEquals("QS7uV522AseVV7xVvXFaxtmuzP", Address.serialize(step.id))

    step.next()
    assertEquals("6p2bG4TuQpEs6bjFq2AW4bVDZwcLnoT7sJ8mCzuV", Address.serialize(step.id))
  }

  @Test
  fun `should deserialize traverse step`() {
    val root = registry.buildTree("hex")
    val traverse = Traverser(1234L, root)

    val step = traverse.iterate(0, 0)
    var actual = Address.deserialize(Address.serialize(step.id))
    for (i in 0..<step.id.position()) {
      assertEquals(step.id[i], actual[i])
    }

    step.next()
    actual = Address.deserialize(Address.serialize(step.id))
    for (i in 0..<step.id.position()) {
      assertEquals(step.id[i], actual[i])
    }

    step.next()
    actual = Address.deserialize(Address.serialize(step.id))
    for (i in 0..<step.id.position()) {
      assertEquals(step.id[i], actual[i])
    }

    step.next()
    actual = Address.deserialize(Address.serialize(step.id))
    for (i in 0..<step.id.position()) {
      assertEquals(step.id[i], actual[i])
    }
  }
}
