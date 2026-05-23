package terrasect.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoiseHandlerTest {

  @Test
  fun `getStrength zero blendWidth returns 1 inside and 0 on boundary or outside`() {
    assertEquals(1f, NoiseHandler.getStrength(0f, -1f), 1e-7f)
    assertEquals(1f, NoiseHandler.getStrength(0f, -100f), 1e-7f)
    assertEquals(0f, NoiseHandler.getStrength(0f, 0f), 1e-7f)
    assertEquals(0f, NoiseHandler.getStrength(0f, 1f), 1e-7f)
  }

  @Test
  fun `getStrength negative blendWidth behaves as hard boundary`() {
    assertEquals(1f, NoiseHandler.getStrength(-1f, -0.5f), 1e-7f)
    assertEquals(0f, NoiseHandler.getStrength(-1f, 0f), 1e-7f)
    assertEquals(0f, NoiseHandler.getStrength(-1f, 1f), 1e-7f)
  }

  @Test
  fun `getStrength outside region always returns 0`() {
    assertEquals(0f, NoiseHandler.getStrength(32f, 0f), 1e-7f)
    assertEquals(0f, NoiseHandler.getStrength(32f, 0.001f), 1e-7f)
    assertEquals(0f, NoiseHandler.getStrength(32f, 1000f), 1e-7f)
  }

  @Test
  fun `getStrength inside region produces linear ramp from 0 at boundary to 1 at blendWidth depth`() {
    assertEquals(0.5f, NoiseHandler.getStrength(32f, -16f), 1e-7f)
    assertEquals(1f, NoiseHandler.getStrength(32f, -32f), 1e-7f)
  }

  @Test
  fun `getStrength deep inside region clamps to 1`() {
    assertEquals(1f, NoiseHandler.getStrength(32f, -100f), 1e-7f)
    assertEquals(1f, NoiseHandler.getStrength(32f, -1000f), 1e-7f)
  }

  @Test
  fun `getStrength quarter and three-quarter positions`() {
    assertEquals(0.25f, NoiseHandler.getStrength(64f, -16f), 1e-7f)
    assertEquals(0.75f, NoiseHandler.getStrength(64f, -48f), 1e-7f)
  }
}
