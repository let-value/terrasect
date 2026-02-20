package terrasect.generation

import org.komputing.kbase58.decodeBase58
import org.komputing.kbase58.encodeToBase58String
import java.nio.ByteBuffer

object Address {
  fun serialize(buffer: ByteBuffer): String {
    return buffer.array().slice(0..<buffer.position()).toByteArray().encodeToBase58String()
  }

  fun deserialize(address: String): ByteBuffer {
    return ByteBuffer.wrap(address.decodeBase58())
  }
}
