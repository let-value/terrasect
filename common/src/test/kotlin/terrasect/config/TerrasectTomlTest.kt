package terrasect.config

import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.ceil
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import terrasect.compat.NoiseRouterCompat
import terrasect.definition.NoiseConstraints
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry
import terrasect.definition.Strategy
import terrasect.helpers.NoiseTransform
import terrasect.instrumentation.MetricsConfig
import terrasect.instrumentation.TerrasectInstr
import terrasect.instrumentation.TerrasectInstrScope
import terrasect.instrumentation.TerrasectMetricEvent
import terrasect.sdf.Decoration
import terrasect.sdf.SiteMetric
import terrasect.strategies.ArchipelagoStrategy
import terrasect.strategies.HexStrategy
import terrasect.strategies.SubdivisionStrategy
import terrasect.strategies.SurroundStrategy
import terrasect.strategies.VoronoiStrategy

class TerrasectTomlTest {
  @Test
  fun `main config parses logging and instrumentation overrides`() {
    val config =
      TerrasectToml.parseConfig(
        """
        preset = "story"

        [logging]
        load_summary = false
        validation_warnings = false
        registry_debug = true

        [instrumentation]
        enabled = true
        counters = true
        timers = false

        [instrumentation.scopes]
        traversal = false

        [instrumentation.scopes.structure]
        enabled = true
        counters = false
        timers = true

        [instrumentation.events]
        "traversal.step" = false
        "structure.generated" = true
        """
          .trimIndent()
      )

    assertAll(
      { assertEquals("story", config.preset) },
      { assertFalse(config.logging.loadSummary) },
      { assertFalse(config.logging.validationWarnings) },
      { assertTrue(config.logging.registryDebug) },
      { assertTrue(config.instrumentation.enabled) },
      { assertTrue(config.instrumentation.counters) },
      { assertFalse(config.instrumentation.timers) },
      {
        assertEquals(
          false,
          config.instrumentation.scopes[TerrasectInstrScope.TRAVERSAL]!!.enabled,
        )
      },
      {
        assertEquals(
          InstrumentationScopeConfig(enabled = true, counters = false, timers = true),
          config.instrumentation.scopes[TerrasectInstrScope.STRUCTURE],
        )
      },
      {
        assertEquals(
          false,
          config.instrumentation.events[TerrasectMetricEvent.TRAVERSAL_STEP],
        )
      },
      {
        assertEquals(
          true,
          config.instrumentation.events[TerrasectMetricEvent.STRUCTURE_GENERATED],
        )
      },
      { assertEquals(config, TerrasectToml.parseConfig(TerrasectTomlWriter.write(config))) },
    )
  }

  @Test
  fun `preset maps every region builder surface`() {
    val warnings = mutableListOf<String>()
    val registry = TerrasectToml.parsePreset(fullPreset(), warning = warnings::add)
    val draft = registry.drafts.getValue("root")
    val root = registry.buildTree("root")

    assertAll(
      { assertEquals("root", registry.getRoot("minecraft:overworld")) },
      { assertEquals(500L, draft.budget) },
      { assertTrue(draft.originAnchor) },
      { assertTrue(root.strategy is SubdivisionStrategy) },
      { assertEquals(-10000L, root.climate!!.temperature!!.min) },
      { assertEquals(10000L, root.climate!!.temperature!!.max) },
      { assertEquals(100L, root.climate!!.humidity!!.min) },
      { assertEquals("rain", root.climate!!.precipitation) },
      { assertEquals("minecraft:normal", root.climate!!.climatePreset) },
      { assertEquals(64, root.height!!.minY) },
      { assertNull(root.height!!.maxY) },
      { assertEquals(setOf("minecraft"), root.biomes!!.allowedMods) },
      { assertEquals(setOf("minecraft:is_forest"), root.biomes!!.allowedTags) },
      { assertEquals(setOf("minecraft:desert"), root.biomes!!.allowedNames) },
      { assertEquals(setOf("example"), root.biomes!!.blockedMods) },
      { assertEquals(setOf("minecraft:is_ocean"), root.biomes!!.blockedTags) },
      { assertEquals(setOf("minecraft:plains"), root.biomes!!.blockedNames) },
      { assertEquals(24, root.structures!!.spacing) },
      { assertEquals(8, root.structures!!.separation) },
      { assertEquals(0.75f, root.structures!!.frequency) },
      { assertEquals(2, root.structures!!.forced.size) },
      { assertEquals(40000L, root.structures!!.forced[0].budget) },
      {
        assertEquals(
          ceil(PI * 5 * 5).toLong(),
          root.structures!!.forced[1].budget,
        )
      },
      { assertEquals(setOf("minecraft:zombie"), root.mobs!!.blockedNames) },
      { assertEquals(setOf("c:foods"), root.loot!!.blockedTags) },
      { assertEquals(5, warnings.size) },
    )

    val noise = root.noise!!
    val transform = noise.noises.getValue("test")
    assertAll(
      { assertEquals(11, transform.operations.size) },
      { assertTrue(transform.operations[0] is NoiseTransform.Clamp) },
      { assertTrue(transform.operations[1] is NoiseTransform.Add) },
      { assertTrue(transform.operations[2] is NoiseTransform.Multiply) },
      { assertTrue(transform.operations[3] is NoiseTransform.Remap) },
      { assertTrue(transform.operations.drop(4).all { it is NoiseTransform.Map }) },
      { assertEquals(16f, noise.blendWidth) },
      { assertTrue("continents" in noise.densityFunctions) },
    )
  }

  @Test
  fun `structure placement properties do not create an empty selection`() {
    val registry =
      TerrasectToml.parsePreset(
        """
        schema = 1
        [roots]
        "minecraft:overworld" = "root"
        [regions.root.structures]
        spacing = 24
        """
          .trimIndent()
      )

    assertNull(registry.buildTree("root").structures!!.selection)
  }

  @Test
  fun `typed preset serializer round trips builder properties`() {
    val preset =
      RegionRegistry().apply {
        setRoot("minecraft:overworld", "root")
        region("root")
          .budget(500)
          .originAnchor()
          .strategy(Strategy.subdivision())
          .climate {
            temperature(-10000, 10000)
            humidity(100)
            continentalness(-200, 300)
            erosion(400)
            depth(-500, 600)
            weirdness(700)
            precipitation("rain")
            climatePreset("minecraft:normal")
          }
          .height { exact(64) }
          .noise {
            blendWidth(NoiseConstraints.DEFAULT_BLEND_WIDTH)
            noise("test") {
              it
                .clamp(-2.0, 2.0)
                .add(1.0)
                .multiply(2.0)
                .remap(-2.0, 6.0, 0.0, 1.0)
                .abs()
                .square()
                .cube()
                .halfNegative()
                .quarterNegative()
                .invert()
                .squeeze()
            }
            densityFunction("continents") { it.add(0.2) }
          }
          .biomes {
            allowMods("minecraft")
            allowTags("minecraft:is_forest")
            allowNames("minecraft:desert")
            blockMods("example")
            blockTags("minecraft:is_ocean")
            blockNames("minecraft:plains")
          }
          .structures {
            allowMods("minecraft")
            spacing(24)
            separation(8)
            frequency(0.75f)
            force("example:ruin")
            force("example:castle", 40000)
            forceRadius("example:tower", 5)
          }
          .mobs { blockNames() }
          .loot { blockTags("c:foods") }
        region("leaf").parent("root").radius(5)
        region("surround").strategy(Strategy.surround("band"))
        region("band").parent("surround").radius(2)
      }

    val registry = TerrasectToml.parsePreset(TerrasectTomlWriter.write(preset))
    val draft = registry.drafts.getValue("root")
    val root = registry.buildTree("root")
    assertAll(
      { assertEquals(500L, draft.budget) },
      { assertTrue(draft.originAnchor) },
      { assertTrue(root.strategy is SubdivisionStrategy) },
      { assertEquals(-10000L, root.climate!!.temperature!!.min) },
      { assertEquals(64, root.height!!.minY) },
      { assertEquals(setOf("minecraft"), root.biomes!!.allowedMods) },
      { assertEquals(3, root.structures!!.forced.size) },
      { assertEquals(5L, root.structures!!.forced[2].radius) },
      { assertEquals(setOf("c:foods"), root.loot!!.blockedTags) },
      { assertEquals(11, root.noise!!.noises.getValue("test").operations.size) },
      { assertEquals(5L, registry.drafts.getValue("leaf").radius) },
      { assertNotNull(root.mobs) },
      { assertTrue(registry.drafts.getValue("root").noiseBuilder.hasExplicitBlendWidth) },
      { assertTrue(registry.drafts.getValue("surround").strategy is SurroundStrategy.Builder) },
    )
  }

  @Test
  fun `single region serializer emits region keys at top level`() {
    val registry = TerrasectToml.parsePreset(fullPreset())
    val toml = TerrasectTomlWriter.write(registry.resolveDraft("root"))

    assertAll(
      { assertTrue("budget = 500" in toml) },
      { assertTrue("[strategy]" in toml) },
      { assertTrue("[climate]" in toml) },
      { assertTrue("schema" !in toml) },
      { assertTrue("[regions" !in toml) },
    )
  }

  @Test
  fun `tiling strategies round trip through toml`() {
    val preset =
      RegionRegistry().apply {
        setRoot("minecraft:overworld", "land")
        region("land").strategy(Strategy.voronoi().tiling())
        region("meadow").parent("land").radius(30).strategy(Strategy.subdivision().tiling())
        region("stripe").parent("meadow").radius(10)
        region("ocean").parent("land").radius(20).strategy(Strategy.archipelago("sea"))
        region("volcano").parent("ocean").radius(8)
        region("sea").budget(120)
      }

    val toml = TerrasectTomlWriter.write(preset)
    val registry = TerrasectToml.parsePreset(toml)
    val land = registry.buildTree("land")
    val meadow = land.children.first { it.name == "meadow" }
    val ocean = land.children.first { it.name == "ocean" }

    assertAll(
      { assertTrue((land.strategy as VoronoiStrategy).tiling) },
      { assertTrue((meadow.strategy as SubdivisionStrategy).tiling) },
      { assertTrue(ocean.strategy is ArchipelagoStrategy) },
      { assertEquals("sea", (ocean.strategy as ArchipelagoStrategy).sea.name) },
      { assertEquals(120L, (ocean.strategy as ArchipelagoStrategy).sea.budget) },
    )
  }

  @Test
  fun `decorations and strategy looks round trip through toml`() {
    val preset =
      RegionRegistry().apply {
        setRoot("minecraft:overworld", "land")
        region("land")
          .strategy(Strategy.voronoi().tiling().metric(SiteMetric.CHEBYSHEV))
          .decoration(Decoration.warp(12f, 48f, 3))
          .decoration(Decoration.stripes(16f, 6f, 30f))
        region("meadow").parent("land").radius(30).strategy(Strategy.hex(null).rounding(10f))
        region("cell").parent("meadow").radius(10)
      }

    val toml = TerrasectTomlWriter.write(preset)
    val registry = TerrasectToml.parsePreset(toml)
    val land = registry.buildTree("land")
    val meadow = land.strategy!!.targets.first { it.name == "meadow" }

    assertAll(
      { assertEquals(SiteMetric.CHEBYSHEV, (land.strategy as VoronoiStrategy).metric) },
      {
        assertEquals(
          listOf(Decoration.warp(12f, 48f, 3), Decoration.stripes(16f, 6f, 30f)),
          land.decorations,
        )
      },
      { assertEquals(10f, (meadow.strategy as HexStrategy).rounding) },
      { assertEquals(toml, TerrasectTomlWriter.write(registry)) },
    )
  }

  @Test
  fun `region declaration order does not change strategy ids`() {
    val first =
      TerrasectToml.parsePreset(
        minimalPreset(
          """
          [regions.zeta]
          parent = "root"
          [regions.root]
          """
        )
      )
    val second =
      TerrasectToml.parsePreset(
        minimalPreset(
          """
          [regions.root]
          [regions.zeta]
          parent = "root"
          """
        )
      )

    assertAll(
      { assertEquals(first.drafts.getValue("root").id, second.drafts.getValue("root").id) },
      { assertEquals(first.drafts.getValue("zeta").id, second.drafts.getValue("zeta").id) },
    )
  }

  @Test
  fun `preset rejects unknown properties and parent cycles`() {
    val unknown =
      assertThrows(TerrasectConfigException::class.java) {
        TerrasectToml.parsePreset(
          minimalPreset(
            """
            [regions.root]
            typo = true
            """
          )
        )
      }
    assertTrue(unknown.message!!.contains("unknown properties: typo"))

    val cycle =
      assertThrows(TerrasectConfigException::class.java) {
        TerrasectToml.parsePreset(
          minimalPreset(
            """
            [regions.root]
            parent = "child"
            [regions.child]
            parent = "root"
            """
          )
        )
      }
    assertTrue(cycle.message!!.contains("region parent cycle"))
  }

  @Test
  fun `defaults create config and version-correct examples without overwriting`(
    @TempDir directory: Path
  ) {
    val created = DefaultConfigFiles.ensurePresent(directory)
    assertEquals(
      setOf("config.toml", "example.toml", "climate_debug.toml"),
      created.map { it.fileName.toString() }.toSet(),
    )
    val configContents = Files.readString(directory.resolve("config.toml"))
    val config = TerrasectToml.parseConfig(configContents)
    assertNull(config.preset)
    assertTrue(configContents.contains("Preset file name without .toml."))
    val example = TerrasectToml.parsePreset(Files.readString(directory.resolve("example.toml")))
    assertEquals(150L * 150L, example.drafts.getValue("overworld").budget)
    val volcanicNoise = example.resolveDraft("volcanic").build().noise!!
    assertEquals(3, volcanicNoise.densityFunctions.size)
    TerrasectToml.parsePreset(Files.readString(directory.resolve("climate_debug.toml")))
    assertTrue(
      Files.readString(directory.resolve("example.toml"))
        .contains(NoiseRouterCompat.SURFACE_FUNCTION_KEY)
    )

    Files.writeString(directory.resolve("config.toml"), "preset = \"custom\"\n")
    assertTrue(DefaultConfigFiles.ensurePresent(directory).isEmpty())
    assertEquals("preset = \"custom\"\n", Files.readString(directory.resolve("config.toml")))
  }

  @Test
  fun `manager registers selected preset and applies runtime settings`(@TempDir configRoot: Path) {
    val directory = Files.createDirectories(configRoot.resolve("terrasect"))
    Files.writeString(
      directory.resolve("config.toml"),
      """
      preset = "custom"
      [logging]
      load_summary = false
      registry_debug = true
      [instrumentation]
      enabled = true
      counters = true
      timers = true
      [instrumentation.scopes.structure]
      counters = false
      """
        .trimIndent(),
    )
    Files.writeString(
      directory.resolve("custom.toml"),
      minimalPreset("[regions.root]\n"),
    )

    try {
      val loaded = TerrasectConfigManager.initialize(configRoot)
      assertAll(
        { assertEquals("custom", PresetRegistry.configuredPresetId) },
        { assertSame(loaded.presets.getValue("custom"), PresetRegistry.resolve(null)) },
        { assertTrue(ConfigLogging.registryDebug) },
        { assertTrue(MetricsConfig.enabled) },
        { assertTrue(MetricsConfig.countersEnabled) },
        { assertTrue(MetricsConfig.timersEnabled) },
        { assertFalse(TerrasectInstr.structure.isCounterEnabled) },
        { assertTrue(TerrasectInstr.structure.isTimingEnabled) },
      )
    } finally {
      PresetRegistry.configuredPresetId = null
      PresetRegistry.presets.remove("custom")
      ConfigLogging.registryDebug = false
      MetricsConfig.enabled = false
      MetricsConfig.countersEnabled = false
      MetricsConfig.timersEnabled = false
      MetricsConfig.clearScopeOverrides()
    }
  }

  private fun minimalPreset(regions: String) =
    """
    schema = 1
    [roots]
    "minecraft:overworld" = "root"
    ${regions.trimIndent()}
    """
      .trimIndent()

  private fun fullPreset() =
    """
    schema = 1

    [roots]
    "minecraft:overworld" = "root"

    [regions.root]
    budget = 500
    origin_anchor = true

    [regions.root.strategy]
    type = "subdivision"

    [regions.root.climate]
    temperature = [-10000, 10000]
    humidity = 100
    continentalness = [-200, 300]
    erosion = 400
    depth = [-500, 600]
    weirdness = 700
    precipitation = "rain"
    climate_preset = "minecraft:normal"

    [regions.root.height]
    exact = 64

    [regions.root.biomes]
    allow_mods = ["minecraft"]
    allow_tags = ["minecraft:is_forest"]
    allow_names = ["minecraft:desert"]
    block_mods = ["example"]
    block_tags = ["minecraft:is_ocean"]
    block_names = ["minecraft:plains"]

    [regions.root.noise]
    blend_width = 16.0

    [regions.root.noise.noises]
    test = [
      { op = "clamp", min = -2.0, max = 2.0 },
      { op = "add", value = 1.0 },
      { op = "multiply", factor = 2.0 },
      { op = "remap", input_min = -2.0, input_max = 6.0, output_min = 0.0, output_max = 1.0 },
      { op = "map", type = "square" },
      { op = "abs" },
      { op = "cube" },
      { op = "half_negative" },
      { op = "quarter_negative" },
      { op = "invert" },
      { op = "squeeze" },
    ]

    [regions.root.noise.density_functions]
    continents = [{ op = "add", value = 0.2 }]

    [regions.root.structures]
    allow_mods = ["minecraft"]
    allow_tags = ["minecraft:village"]
    allow_names = ["minecraft:village_plains"]
    block_mods = ["example"]
    block_tags = ["minecraft:ruins"]
    block_names = ["minecraft:mineshaft"]
    spacing = 24
    separation = 8
    frequency = 0.75
    force = [
      { name = "example:castle", budget = 40000 },
      { name = "example:tower", radius = 5 },
    ]

    [regions.root.mobs]
    block_names = ["minecraft:zombie"]

    [regions.root.loot]
    block_tags = ["c:foods"]

    [regions.hex]
    parent = "root"
    radius = 10
    [regions.hex.strategy]
    type = "hex"
    tiling = false
    ring_region = "hex_ring"

    [regions.hex_center]
    parent = "hex"
    radius = 5

    [regions.hex_ring]
    parent = "hex"
    radius = 2

    [regions.surround]
    parent = "root"
    radius = 8
    [regions.surround.strategy]
    type = "surround"
    surround_region = "surround_band"

    [regions.surround_center]
    parent = "surround"
    radius = 4

    [regions.surround_band]
    parent = "surround"
    radius = 2

    [regions.voronoi]
    parent = "root"
    radius = 6
    [regions.voronoi.strategy]
    type = "voronoi"

    [regions.voronoi_leaf]
    parent = "voronoi"
    radius = 3
    """
      .trimIndent()
}
