package terrasect.definition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class RegionDefinitionTest {
  @Test
  fun shouldUseNestedBuilders() {
    RegionDefinition.builder()
        .biomes { it.allowMods("minecraft") }
        .climate { it.temperature(10.0f).depth(10f) }
        .noise { noise -> noise.noise("factor") { t -> t.clamp(1.0, 2.0) } }
        .build()
  }

  @Test
  fun inheritParentReturnsSameInstanceWhenParentNull() {
    val definition = RegionDefinition()

    val result = definition.inheritParent(null)

    assertSame(definition, result)
  }

  @Test
  fun inheritParentUsesChildAndParentValues() {
    val parentClimate = ClimateSettings.builder().temperature(0.1f).build()
    val childClimate = ClimateSettings.builder().humidity(0.5f).build()
    val parentHeight = HeightConstraints.builder().range(-64, 320).build()
    val childHeight = HeightConstraints.builder().exact(90).build()
    val parentStrategy = GenerationStrategy.voronoi(7)

    val parent =
        RegionDefinition(
            climate = parentClimate,
            height = parentHeight,
            generationStrategy = parentStrategy,
        )

    val child =
        RegionDefinition(
            climate = childClimate,
            height = childHeight,
        )

    val merged = child.inheritParent(parent)

    assertEquals(0.1f, merged.climate?.temperature?.min)
    assertEquals(0.1f, merged.climate?.temperature?.max)
    assertEquals(0.5f, merged.climate?.humidity?.min)
    assertEquals(0.5f, merged.climate?.humidity?.max)
    assertEquals(90, merged.height?.minY)
    assertEquals(320, merged.height?.maxY)
    assertSame(parentStrategy, merged.generationStrategy)
  }
}
