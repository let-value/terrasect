package terrasect.definition

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import terrasect.strategies.HexStrategy

class RegionDefinitionTest {
  val registry = RegionRegistry()

  @Test
  fun `designer can define a focused region without extra constraints`() {
    val region =
        registry
            .region("whispering_pines")
            .climate {
              temperature(-0.3f, 0.2f)
              humidity(0.6f)
              precipitation("snow")
            }
            .height { range(80, 160) }
            .biomes {
              allowTags("coniferous", "taiga")
              blockNames("minecraft:swamp")
            }
            .strategy(HexStrategy.builder("whispering_pines_ring"))
            .build()

    assertAll(
        { assertNotNull(region.climate) },
        { assertEquals(-0.3f, region.climate!!.temperature!!.min) },
        { assertEquals(0.2f, region.climate!!.temperature!!.max) },
        { assertEquals(0.6f, region.climate!!.humidity!!.min) },
        { assertEquals(0.6f, region.climate!!.humidity!!.max) },
        { assertEquals("snow", region.climate!!.precipitation) },
        { assertEquals(80, region.height!!.minY) },
        { assertEquals(160, region.height!!.maxY) },
        { assertEquals(setOf("coniferous", "taiga"), region.biomes!!.allowedTags) },
        { assertEquals(setOf("minecraft:swamp"), region.biomes!!.blockedNames) },
        { assertNull(region.noise) },
        { assertNull(region.structures) },
        { assertNull(region.mobs) },
        { assertTrue(region.strategy is HexStrategy.Builder) },
        {
          assertEquals(
              "whispering_pines_ring",
              (region.strategy as HexStrategy.Builder).ringRegionName,
          )
        },
    )
  }

  @Test
  fun `child region inherits narrative defaults but keeps its overrides`() {
    val worldDefaults =
        registry
            .region("parent")
            .climate {
              temperature(-0.6f, -0.2f)
              humidity(0.7f, 0.9f)
              precipitation("snow")
            }
            .height { range(48, 96) }
            .biomes { allowTags("taiga", "coniferous") }
            .structures {
              allowNames("minecraft:village")
              blockTags("ruins")
            }
            .strategy(Strategy.subdivision(0.2f))

    val child =
        registry
            .region("child")
            .climate { temperature(-0.1f) }
            .biomes { blockNames("minecraft:old_growth_pine_taiga") }
            .inheritParent(worldDefaults)
            .build()

    assertAll(
        { assertEquals(-0.1f, child.climate!!.temperature!!.min) },
        { assertEquals(-0.1f, child.climate!!.temperature!!.max) },
        { assertEquals(0.7f, child.climate!!.humidity!!.min) },
        { assertEquals(0.9f, child.climate!!.humidity!!.max) },
        { assertEquals("snow", child.climate!!.precipitation) },
        { assertEquals(48, child.height!!.minY) },
        { assertEquals(96, child.height!!.maxY) },
        { assertEquals(setOf("taiga", "coniferous"), child.biomes!!.allowedTags) },
        { assertEquals(setOf("minecraft:old_growth_pine_taiga"), child.biomes!!.blockedNames) },
        { assertEquals(setOf("minecraft:village"), child.structures!!.allowedNames) },
        { assertEquals(setOf("ruins"), child.structures!!.blockedTags) },
        { assertTrue(child.strategy is SubdivisionSettings.Builder) },
        { assertEquals(0.2f, (child.strategy as SubdivisionSettings.Builder).jitter) },
        { assertNull(child.noise) },
        { assertNull(child.mobs) },
    )
  }
}
