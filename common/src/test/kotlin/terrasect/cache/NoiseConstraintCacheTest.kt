package terrasect.cache

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import terrasect.definition.NoiseConstraints
import terrasect.definition.Region
import terrasect.lookup.CompiledNoiseRegistry

class NoiseConstraintCacheTest {

  private fun makeRegion(name: String, noise: NoiseConstraints?): Region {
    return Region(name, 10000, emptySet(), noise = noise)
  }

  private fun buildCache(
      region: Region,
      registry: CompiledNoiseRegistry,
      distance: Float,
      width: Int = 4,
      height: Int = 4,
  ): NoiseConstraintCache {
    val chunkCache = ChunkCache()
    val g = GridCache<Region>(width, height, 0, 0)
    val d = FloatArray(width * height) { distance }
    for (x in 0 until width) {
      for (z in 0 until height) {
        g.add(x, z, region)
      }
    }
    chunkCache.regions = g
    chunkCache.distances = d
    chunkCache.noiseConstraintCache = NoiseConstraintCache(chunkCache, registry)
    return chunkCache.noiseConstraintCache!!
  }

  @Test
  fun `returns null when no region has constraints`() {
    val region = makeRegion("empty", null)
    val registry = CompiledNoiseRegistry.build(region)
    assertNull(registry)
  }

  @Test
  fun `returns null constraints for unconstrained region`() {
    val constrained =
        makeRegion(
            "has_noise",
            NoiseConstraints.builder()
                .densityFunction("minecraft:overworld/depth") { it.clamp(-0.5, 0.5) }
                .build(),
        )
    val unconstrained = makeRegion("no_noise", null)

    val registry = CompiledNoiseRegistry.build(constrained)!!

    val chunkCache = ChunkCache()
    val g = GridCache<Region>(4, 4, 0, 0)
    val d = FloatArray(16) { -100f }
    for (x in 0 until 4) for (z in 0 until 4) g.add(x, z, unconstrained)
    chunkCache.regions = g
    chunkCache.distances = d
    val cache = NoiseConstraintCache(chunkCache, registry)

    assertNull(cache.getConstraints(0, 0))
  }

  @Test
  fun `full strength deep inside region`() {
    val noise =
        NoiseConstraints.builder()
            .densityFunction("minecraft:overworld/depth") { it.clamp(-0.5, 0.5) }
            .blendWidth(32f)
            .build()
    val region = makeRegion("constrained", noise)
    val registry = CompiledNoiseRegistry.build(region)!!

    val cache = buildCache(region, registry, distance = -100f)

    assertEquals(1f, cache.getStrength(0, 0))
    assertNotNull(cache.getConstraints(0, 0))
  }

  @Test
  fun `blend strength varies with SDF distance`() {
    val noise =
        NoiseConstraints.builder()
            .densityFunction("minecraft:overworld/depth") { it.clamp(-0.5, 0.5) }
            .blendWidth(32f)
            .build()
    val region = makeRegion("constrained", noise)
    val registry = CompiledNoiseRegistry.build(region)!!

    val cache = buildCache(region, registry, distance = -16f)

    assertEquals(0.5f, cache.getStrength(0, 0), 0.01f)
  }

  @Test
  fun `strength is zero at region boundary`() {
    val noise =
        NoiseConstraints.builder()
            .densityFunction("minecraft:overworld/depth") { it.clamp(-0.5, 0.5) }
            .blendWidth(32f)
            .build()
    val region = makeRegion("constrained", noise)
    val registry = CompiledNoiseRegistry.build(region)!!

    val cache = buildCache(region, registry, distance = 0f)

    assertEquals(0f, cache.getStrength(0, 0))
  }

  @Test
  fun `strength is zero outside region`() {
    val noise =
        NoiseConstraints.builder()
            .densityFunction("minecraft:overworld/depth") { it.clamp(-0.5, 0.5) }
            .blendWidth(32f)
            .build()
    val region = makeRegion("constrained", noise)
    val registry = CompiledNoiseRegistry.build(region)!!

    val cache = buildCache(region, registry, distance = 10f)

    assertEquals(0f, cache.getStrength(0, 0))
  }
}
