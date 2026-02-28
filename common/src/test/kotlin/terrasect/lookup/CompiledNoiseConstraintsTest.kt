package terrasect.lookup

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import terrasect.definition.NoiseConstraints

class CompiledNoiseRegistryTest {

  @Test
  fun `hasAnyConstraints reflects density function and noise entries`() {
    val empty = NoiseConstraints.builder().build()
    assertFalse(empty.hasAnyConstraints())

    val withDensity =
        NoiseConstraints.builder()
            .densityFunction("minecraft:overworld/depth") { it.clamp(-1.0, 1.0) }
            .build()
    assertTrue(withDensity.hasAnyConstraints())

    val withNoise =
        NoiseConstraints.builder()
            .noise("minecraft:temperature") { it.multiply(0.5) }
            .build()
    assertTrue(withNoise.hasAnyConstraints())
  }

  @Test
  fun `blendWidth defaults and propagates through builder`() {
    val defaultConstraints = NoiseConstraints.builder().build()
    assertEquals(NoiseConstraints.DEFAULT_BLEND_WIDTH, defaultConstraints.blendWidth)

    val custom = NoiseConstraints.builder().blendWidth(64f).build()
    assertEquals(64f, custom.blendWidth)
  }

  @Test
  fun `blendWidth inherits from parent when not set`() {
    val parent = NoiseConstraints.builder().blendWidth(48f)
    val child = NoiseConstraints.builder().inheritParent(parent).build()
    assertEquals(48f, child.blendWidth)
  }

  @Test
  fun `blendWidth child overrides parent`() {
    val parent = NoiseConstraints.builder().blendWidth(48f)
    val child = NoiseConstraints.builder().blendWidth(16f).inheritParent(parent).build()
    assertEquals(16f, child.blendWidth)
  }
}
