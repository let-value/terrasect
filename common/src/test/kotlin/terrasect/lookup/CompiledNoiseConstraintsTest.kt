package terrasect.lookup

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import terrasect.definition.NoiseConstraints
import terrasect.definition.Region

class NoiseConstraintsBuilderTest {

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
      NoiseConstraints.builder().noise("minecraft:temperature") { it.multiply(0.5) }.build()
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

  @Test
  fun `blendWidth explicit default is preserved over parent non-default`() {
    val parent = NoiseConstraints.builder().blendWidth(48f)
    val child =
      NoiseConstraints.builder()
        .blendWidth(NoiseConstraints.DEFAULT_BLEND_WIDTH)
        .inheritParent(parent)
        .build()
    assertEquals(NoiseConstraints.DEFAULT_BLEND_WIDTH, child.blendWidth)
  }

  @Test
  fun `build produces independent snapshot - further builder mutations do not affect built instance`() {
    val builder = NoiseConstraints.builder().densityFunction("depth") { it.multiply(0.5) }
    val first = builder.build()
    builder.densityFunction("temperature") { it.add(0.1) }
    val second = builder.build()

    assertFalse(first.noises.containsKey("temperature"))
    assertFalse(first.densityFunctions.containsKey("temperature"))
    assertTrue(second.densityFunctions.containsKey("temperature"))
  }
}

class CompiledNoiseRegistryTest {

  private fun noise(vararg entries: Pair<String, Double>) =
    NoiseConstraints.builder()
      .apply { entries.forEach { (k, v) -> densityFunction(k) { it.multiply(v) } } }
      .build()

  @Test
  fun `build returns null when no region has noise constraints`() {
    val root = Region("root", 1000, emptySet())
    assertNull(CompiledNoiseRegistry.build(root))
  }

  @Test
  fun `build includes root region with constraints`() {
    val root = Region("root", 1000, emptySet(), noise = noise("depth" to 0.5))
    val registry = CompiledNoiseRegistry.build(root)
    assertNotNull(registry)
    assertNotNull(registry!!.get(root))
  }

  @Test
  fun `build includes child region with constraints and skips root without`() {
    val child = Region("child", 500, emptySet(), noise = noise("depth" to 0.5))
    val root = Region("root", 1000, setOf(child))
    val registry = CompiledNoiseRegistry.build(root)
    assertNotNull(registry)
    assertNull(registry!!.get(root))
    assertNotNull(registry.get(child))
  }

  @Test
  fun `build collects multiple regions independently`() {
    val child1 = Region("child1", 400, emptySet(), noise = noise("depth" to 0.5))
    val child2 = Region("child2", 400, emptySet(), noise = noise("continents" to 2.0))
    val root = Region("root", 1000, setOf(child1, child2))
    val registry = CompiledNoiseRegistry.build(root)!!
    assertNotNull(registry.get(child1))
    assertNotNull(registry.get(child2))
    assertNull(registry.get(root))
  }

  @Test
  fun `blendWidth is accessible through returned NoiseConstraints`() {
    val constraints =
      NoiseConstraints.builder()
        .densityFunction("depth") { it.multiply(0.5) }
        .blendWidth(64f)
        .build()
    val root = Region("root", 1000, emptySet(), noise = constraints)
    val registry = CompiledNoiseRegistry.build(root)!!
    assertEquals(64f, registry.get(root)!!.blendWidth)
  }
}

class NarrativeNoiseConstraintsTest {

  @Test
  fun `desert world pins temperature to hottest band and continentalness to inland`() {
    val constraints =
      NoiseConstraints.builder()
        .noise("minecraft:temperature") { it.clamp(0.55, 1.0) }
        .densityFunction("minecraft:overworld/continents") { it.clamp(-0.11, 1.0) }
        .build()

    assertTrue(constraints.hasAnyConstraints())
    assertTrue(constraints.noises.containsKey("minecraft:temperature"))
    assertTrue(constraints.densityFunctions.containsKey("minecraft:overworld/continents"))

    val temp = constraints.noises["minecraft:temperature"]!!
    assertEquals(0.55, temp.apply(-0.9), 1e-9)
    assertEquals(0.7, temp.apply(0.7), 1e-9)

    val continents = constraints.densityFunctions["minecraft:overworld/continents"]!!
    assertEquals(-0.11, continents.apply(-0.9), 1e-9)
    assertEquals(0.5, continents.apply(0.5), 1e-9)
  }

  @Test
  fun `river world clamps weirdness to valley band and erosion to highest index`() {
    val constraints =
      NoiseConstraints.builder()
        .densityFunction("minecraft:overworld/ridges_folded") { it.clamp(-0.05, 0.05) }
        .densityFunction("minecraft:overworld/erosion") { it.clamp(0.55, 1.0) }
        .build()

    assertTrue(constraints.hasAnyConstraints())
    assertTrue(constraints.densityFunctions.containsKey("minecraft:overworld/ridges_folded"))
    assertTrue(constraints.densityFunctions.containsKey("minecraft:overworld/erosion"))

    val ridges = constraints.densityFunctions["minecraft:overworld/ridges_folded"]!!
    assertEquals(-0.05, ridges.apply(-0.8), 1e-9)
    assertEquals(0.05, ridges.apply(0.8), 1e-9)
    assertEquals(0.0, ridges.apply(0.0), 1e-9)

    val erosion = constraints.densityFunctions["minecraft:overworld/erosion"]!!
    assertEquals(0.55, erosion.apply(-0.5), 1e-9)
    assertEquals(0.7, erosion.apply(0.7), 1e-9)
  }

  @Test
  fun `lake world restricts continents to coast band with flat high-erosion terrain`() {
    val constraints =
      NoiseConstraints.builder()
        .densityFunction("minecraft:overworld/continents") { it.clamp(-0.455, -0.11) }
        .densityFunction("minecraft:overworld/erosion") { it.clamp(0.45, 1.0) }
        .build()

    assertTrue(constraints.hasAnyConstraints())
    assertTrue(constraints.densityFunctions.containsKey("minecraft:overworld/continents"))
    assertTrue(constraints.densityFunctions.containsKey("minecraft:overworld/erosion"))

    val continents = constraints.densityFunctions["minecraft:overworld/continents"]!!
    assertEquals(-0.455, continents.apply(-0.9), 1e-9)
    assertEquals(-0.11, continents.apply(0.5), 1e-9)
    assertEquals(-0.3, continents.apply(-0.3), 1e-9)

    val erosion = constraints.densityFunctions["minecraft:overworld/erosion"]!!
    assertEquals(0.45, erosion.apply(-0.5), 1e-9)
    assertEquals(0.7, erosion.apply(0.7), 1e-9)
  }

  @Test
  fun `ocean world forces deep ocean continentalness and amplifies depth below sea level`() {
    val constraints =
      NoiseConstraints.builder()
        .densityFunction("minecraft:overworld/continents") { it.clamp(-1.05, -0.455) }
        .densityFunction("minecraft:overworld/depth") { it.multiply(2.0) }
        .build()

    assertTrue(constraints.hasAnyConstraints())
    assertTrue(constraints.densityFunctions.containsKey("minecraft:overworld/continents"))
    assertTrue(constraints.densityFunctions.containsKey("minecraft:overworld/depth"))

    val continents = constraints.densityFunctions["minecraft:overworld/continents"]!!
    assertEquals(-1.05, continents.apply(-1.2), 1e-9)
    assertEquals(-0.455, continents.apply(0.5), 1e-9)
    assertEquals(-0.7, continents.apply(-0.7), 1e-9)

    val depth = constraints.densityFunctions["minecraft:overworld/depth"]!!
    assertEquals(1.0, depth.apply(0.5), 1e-9)
    assertEquals(-1.0, depth.apply(-0.5), 1e-9)
  }
}
