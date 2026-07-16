package terrasect.definition

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import terrasect.strategies.HexStrategy
import terrasect.strategies.VoronoiStrategy

class RegionDefinitionTest {
  val registry = RegionRegistry()

  @Test
  fun `designer can define a focused region without extra constraints`() {
    val region =
      registry
        .region("whispering_pines")
        .climate {
          temperature(-300, 200)
          humidity(600)
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
      { assertEquals(-300, region.climate!!.temperature!!.min) },
      { assertEquals(200, region.climate!!.temperature!!.max) },
      { assertEquals(600, region.climate!!.humidity!!.min) },
      { assertEquals(600, region.climate!!.humidity!!.max) },
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
  fun `loot constraints are propagated through buildTree`() {
    registry.region("loot_test_root").loot {
      allowNames("minecraft:diamond")
      blockTags("c:tools")
    }
    registry.setRoot("overworld", "loot_test_root")

    val root = registry.buildTree("loot_test_root")

    assertAll(
      { assertNotNull(root.loot) },
      { assertEquals(setOf("minecraft:diamond"), root.loot!!.allowedNames) },
      { assertEquals(setOf("c:tools"), root.loot!!.blockedTags) },
    )
  }

  @Test
  fun `mob constraints are propagated through buildTree`() {
    registry.region("mob_test_root").mobs {
      blockNames("minecraft:zombie")
      allowTags("c:passive")
    }

    val root = registry.buildTree("mob_test_root")

    assertAll(
      { assertNotNull(root.mobs) },
      { assertEquals(setOf("minecraft:zombie"), root.mobs!!.blockedNames) },
      { assertEquals(setOf("c:passive"), root.mobs!!.allowedTags) },
    )
  }

  @Test
  fun `forced structures survive buildTree on the declaring region only`() {
    registry.region("forced_root").structures { force("mymod:castle", budget = 40000) }
    registry.region("forced_child").apply {
      structuresBuilder
      parent("forced_root")
    }

    val root = registry.buildTree("forced_root")

    assertAll(
      { assertEquals(listOf(ForcedStructure("mymod:castle", 40000)), root.structures!!.forced) },
      {
        val child = root.children.single { it.name == "forced_child" }
        assertTrue(child.structures!!.forced.isEmpty())
      },
    )
  }

  @Test
  fun `visiting marker is cleared after a child build throws, so a later build is not permanently poisoned`() {
    val throwingStrategy =
      object : StrategySettings {
        override fun build(builder: RegionBuilder, children: Set<Region>): Strategy =
          throw IllegalStateException("boom")
      }
    registry.region("flaky").strategy(throwingStrategy)

    assertThrows(IllegalStateException::class.java) { registry.buildTree("flaky") }

    // Before the buildTree try/finally fix, the exception above left "flaky" stuck in the
    // registry's internal `visiting` set, so this second (now-valid) build would incorrectly be
    // treated as a self-cycle and silently return an empty region instead of throwing or building.
    registry.region("flaky").strategy(HexStrategy.builder())
    val region = registry.buildTree("flaky")

    assertTrue(region.strategy is HexStrategy)
  }

  @Test
  fun `child region inherits narrative defaults but keeps its overrides`() {
    val worldDefaults =
      registry
        .region("parent")
        .climate {
          temperature(-600, -200)
          humidity(700, 900)
          precipitation("snow")
        }
        .height { range(48, 96) }
        .biomes { allowTags("taiga", "coniferous") }
        .structures {
          allowNames("minecraft:village")
          blockTags("ruins")
        }
        .strategy(Strategy.voronoi())

    val child =
      registry
        .region("child")
        .climate { temperature(100) }
        .biomes { blockNames("minecraft:old_growth_pine_taiga") }
        .inheritParent(worldDefaults)
        .build()

    assertAll(
      { assertEquals(100, child.climate!!.temperature!!.min) },
      { assertEquals(100, child.climate!!.temperature!!.max) },
      { assertEquals(700, child.climate!!.humidity!!.min) },
      { assertEquals(900, child.climate!!.humidity!!.max) },
      { assertEquals("snow", child.climate!!.precipitation) },
      { assertEquals(48, child.height!!.minY) },
      { assertEquals(96, child.height!!.maxY) },
      { assertEquals(setOf("taiga", "coniferous"), child.biomes!!.allowedTags) },
      { assertEquals(setOf("minecraft:old_growth_pine_taiga"), child.biomes!!.blockedNames) },
      { assertEquals(setOf("minecraft:village"), child.structures!!.selection!!.allowedNames) },
      { assertEquals(setOf("ruins"), child.structures!!.selection!!.blockedTags) },
      { assertTrue(child.strategy is VoronoiStrategy.Builder) },
      { assertNull(child.noise) },
      { assertNull(child.mobs) },
    )
  }
}
