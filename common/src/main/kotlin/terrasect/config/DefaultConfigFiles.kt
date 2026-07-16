package terrasect.config

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import terrasect.compat.NoiseRouterCompat

object DefaultConfigFiles {
  const val CONFIG_FILE = "config.toml"

  private val presets =
    mapOf(
      "example.toml" to examplePreset(),
      "climate_debug.toml" to climateDebugPreset(),
    )

  fun ensurePresent(directory: Path): List<Path> {
    Files.createDirectories(directory)
    val existingPresets = presetFiles(directory)
    val created = mutableListOf<Path>()
    writeNew(directory.resolve(CONFIG_FILE), defaultConfig())?.let(created::add)
    if (existingPresets.isEmpty()) {
      for ((name, contents) in presets) {
        writeNew(directory.resolve(name), contents)?.let(created::add)
      }
    }
    return created
  }

  fun presetFiles(directory: Path): List<Path> {
    if (!Files.isDirectory(directory)) return emptyList()
    return Files.list(directory).use { files ->
      files
        .filter(Files::isRegularFile)
        .filter { it.fileName.toString().endsWith(".toml") }
        .filter { it.fileName.toString() != CONFIG_FILE }
        .sorted()
        .toList()
    }
  }

  private fun writeNew(path: Path, contents: String): Path? =
    try {
      Files.writeString(path, contents, StandardOpenOption.CREATE_NEW)
      path
    } catch (_: FileAlreadyExistsException) {
      null
    }

  private fun defaultConfig() =
    """
    # Preset file name without .toml. Leave empty to keep Terrasect inactive.
    preset = ""

    [logging]
    load_summary = true
    validation_warnings = true
    registry_debug = false

    [instrumentation]
    enabled = false
    counters = false
    timers = false

    [instrumentation.scopes]
    traversal = false

    [instrumentation.events]
    "traversal.step" = false
    """
      .trimIndent() + "\n"

  private fun examplePreset() =
    """
    schema = 1

    [roots]
    "minecraft:overworld" = "overworld"

    [regions.overworld]
    radius = 150
    origin_anchor = true

    [regions.overworld.strategy]
    type = "hex"
    tiling = true
    ring_region = "border"

    [regions.overworld.structures]
    allow_mods = ["minecraft"]
    spacing = 24
    separation = 8
    frequency = 1.0
    force = [{ name = "minecraft:village_plains", radius = 96 }]

    [regions.overworld.mobs]
    block_names = ["minecraft:zombie"]

    [regions.overworld.loot]
    block_tags = ["c:foods"]

    [regions.cell]
    parent = "overworld"

    [regions.cell.strategy]
    type = "voronoi"

    [regions.border]
    parent = "overworld"
    radius = 12

    [regions.cold_forest]
    parent = "cell"
    radius = 75

    [regions.cold_forest.climate]
    temperature = [-10000, -2000]
    humidity = [4000, 10000]
    precipitation = "snow"
    climate_preset = "minecraft:normal"

    [regions.cold_forest.height]
    range = [60, 200]

    [regions.cold_forest.biomes]
    allow_tags = ["minecraft:is_taiga"]

    [regions.volcanic]
    parent = "cell"
    radius = 45

    [regions.volcanic.climate]
    temperature = 8000
    humidity = -3000

    [regions.volcanic.noise]
    blend_width = 24.0

    [regions.volcanic.noise.density_functions]
    continents = [
      { op = "multiply", factor = 0.0 },
      { op = "add", value = 0.35 },
    ]
    "${NoiseRouterCompat.SURFACE_FUNCTION_KEY}" = [{ op = "add", value = 12.0 }]
    finalDensity = [{ op = "add", value = 0.02 }]
    """
      .trimIndent() + "\n"

  private fun climateDebugPreset() =
    """
    schema = 1

    [roots]
    "minecraft:overworld" = "hex"

    [regions.hex]
    radius = 150

    [regions.hex.strategy]
    type = "hex"
    tiling = true

    [regions.cell]
    parent = "hex"

    [regions.cell.strategy]
    type = "voronoi"

    [regions.cold]
    parent = "cell"
    radius = 30

    [regions.cold.climate]
    temperature = -10000
    humidity = 5000

    [regions.temperate]
    parent = "cell"
    radius = 45

    [regions.temperate.climate]
    temperature = 0
    humidity = 0

    [regions.hot]
    parent = "cell"
    radius = 75

    [regions.hot.climate]
    temperature = 10000
    humidity = -5000
    """
      .trimIndent() + "\n"
}
