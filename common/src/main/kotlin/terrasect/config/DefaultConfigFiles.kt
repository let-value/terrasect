package terrasect.config

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import terrasect.compat.NoiseRouterCompat
import terrasect.definition.RegionRegistry
import terrasect.definition.Strategy
import terrasect.instrumentation.TerrasectInstrScope
import terrasect.instrumentation.TerrasectMetricEvent

object DefaultConfigFiles {
  const val CONFIG_FILE = "config.toml"

  private val defaultConfig =
    TerrasectConfig(
      instrumentation =
        InstrumentationConfig(
          scopes =
            mapOf(TerrasectInstrScope.TRAVERSAL to InstrumentationScopeConfig(enabled = false)),
          events = mapOf(TerrasectMetricEvent.TRAVERSAL_STEP to false),
        )
    )

  private val examplePreset =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld")
      region("overworld")
        .radius(150)
        .originAnchor()
        .strategy(Strategy.hex("border").tiling())
        .structures {
          allowMods("minecraft")
          spacing(24)
          separation(8)
          frequency(1.0f)
          forceRadius("minecraft:village_plains", 96)
        }
        .mobs { blockNames("minecraft:zombie") }
        .loot { blockTags("c:foods") }
      region("cell").parent("overworld").strategy(Strategy.voronoi())
      region("border").parent("overworld").radius(12)
      region("cold_forest")
        .parent("cell")
        .radius(75)
        .climate {
          temperature(-10000, -2000)
          humidity(4000, 10000)
          precipitation("snow")
          climatePreset("minecraft:normal")
        }
        .height { range(60, 200) }
        .biomes { allowTags("minecraft:is_taiga") }
      region("volcanic")
        .parent("cell")
        .radius(45)
        .climate { temperature(8000).humidity(-3000) }
        .noise {
          blendWidth(24.0f)
          densityFunction("continents") { it.multiply(0.0).add(0.35) }
          densityFunction(NoiseRouterCompat.SURFACE_FUNCTION_KEY) { it.add(12.0) }
          densityFunction("finalDensity") { it.add(0.02) }
        }
    }

  private val climateDebugPreset =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "hex")
      region("hex").radius(150).strategy(Strategy.hex().tiling())
      region("cell").parent("hex").strategy(Strategy.voronoi())
      region("cold").parent("cell").radius(30).climate {
        temperature(-10000).humidity(5000)
      }
      region("temperate").parent("cell").radius(45).climate {
        temperature(0).humidity(0)
      }
      region("hot").parent("cell").radius(75).climate {
        temperature(10000).humidity(-5000)
      }
    }

  private val presets =
    mapOf("example.toml" to examplePreset, "climate_debug.toml" to climateDebugPreset)

  fun ensurePresent(directory: Path): List<Path> {
    Files.createDirectories(directory)
    val existingPresets = presetFiles(directory)
    val created = mutableListOf<Path>()
    writeNew(directory.resolve(CONFIG_FILE), TerrasectTomlWriter.write(defaultConfig))
      ?.let(created::add)
    if (existingPresets.isEmpty()) {
      for ((name, preset) in presets) {
        writeNew(directory.resolve(name), TerrasectTomlWriter.write(preset))?.let(created::add)
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
}
