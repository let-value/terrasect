package terrasect.generation

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.ChunkAccess
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import terrasect.extender.ChunkAccessExtender

class ChunkContextTest {
  @Test
  fun `chunk contexts ignore levels without a dimension key`() {
    SharedConstants.tryDetectVersion()
    Bootstrap.bootStrap()
    val level = mock(Level::class.java)
    doReturn(null).`when`(level).dimension()
    val chunk =
      object : ChunkAccessExtender {
        override fun `terrasect$getContext`(): ChunkContext? = null

        override fun `terrasect$getChunk`(): ChunkAccess? = null

        override fun `terrasect$getLevel`() = level
      }

    assertNull(ChunkContext(chunk, ChunkPos(0, 0)).dimensionContext)
  }
}
