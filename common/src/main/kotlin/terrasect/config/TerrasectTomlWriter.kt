package terrasect.config

import com.electronwill.nightconfig.core.CommentedConfig
import com.electronwill.nightconfig.core.UnmodifiableConfig
import com.electronwill.nightconfig.toml.TomlWriter
import java.io.StringWriter
import java.util.Collections
import java.util.IdentityHashMap
import terrasect.definition.ClimateConstraints
import terrasect.definition.HeightConstraints
import terrasect.definition.NoiseConstraints
import terrasect.definition.RegionBuilder
import terrasect.definition.RegionRegistry
import terrasect.definition.SelectionConstraints
import terrasect.definition.StrategySettings
import terrasect.definition.StructureConstraints
import terrasect.helpers.NoiseTransform
import terrasect.strategies.ArchipelagoStrategy
import terrasect.strategies.HexStrategy
import terrasect.strategies.SubdivisionStrategy
import terrasect.strategies.SurroundStrategy
import terrasect.strategies.VoronoiStrategy

object TerrasectTomlWriter {
  fun write(config: TerrasectConfig): String =
    Document().apply { mainConfig(config) }.write("preset", PRESET_COMMENT)

  fun write(preset: RegionRegistry): String = Document().apply { preset(preset) }.write()

  private class Document {
    private val root = CommentedConfig.inMemory()
    private val inlineTables: MutableSet<UnmodifiableConfig> =
      Collections.newSetFromMap(IdentityHashMap())

    fun mainConfig(config: TerrasectConfig) {
      root.value("preset", config.preset.orEmpty())
      root.table("logging") {
        value("load_summary", config.logging.loadSummary)
        value("validation_warnings", config.logging.validationWarnings)
        value("registry_debug", config.logging.registryDebug)
      }
      root.table("instrumentation") {
        value("enabled", config.instrumentation.enabled)
        value("counters", config.instrumentation.counters)
        value("timers", config.instrumentation.timers)
        if (config.instrumentation.scopes.isNotEmpty()) {
          table("scopes") {
            for ((scope, options) in config.instrumentation.scopes.entries.sortedBy { it.key.id }) {
              if (options.counters == null && options.timers == null && options.enabled != null) {
                value(scope.id, options.enabled)
              } else {
                table(scope.id) {
                  optional("enabled", options.enabled)
                  optional("counters", options.counters)
                  optional("timers", options.timers)
                }
              }
            }
          }
        }
        if (config.instrumentation.events.isNotEmpty()) {
          table("events") {
            for ((event, enabled) in config.instrumentation.events.entries.sortedBy { it.key.id }) {
              value(event.id, enabled)
            }
          }
        }
      }
    }

    fun preset(preset: RegionRegistry) {
      root.value("schema", TerrasectToml.SCHEMA_VERSION)
      root.table("roots") {
        for ((dimension, region) in preset.dimensionRoots.toSortedMap()) value(dimension, region)
      }
      root.table("regions") {
        for ((name, builder) in preset.drafts.toSortedMap()) table(name) { region(builder) }
      }
    }

    fun write(commentedKey: String? = null, comment: String? = null): String {
      if (commentedKey != null && comment != null) root.setComment(listOf(commentedKey), comment)
      val output = StringWriter()
      TomlWriter()
        .apply { setWriteTableInlinePredicate(inlineTables::contains) }
        .write(root, output)
      return output.toString().let { if (it.endsWith('\n')) it else "$it\n" }
    }

    private fun CommentedConfig.region(builder: RegionBuilder) {
      optional("parent", builder.parent)
      val radius = builder.radius
      if (radius != null && builder.budget == radius * radius) {
        value("radius", radius)
      } else {
        optional("budget", builder.budget)
      }
      if (builder.originAnchor) value("origin_anchor", true)
      builder.strategy?.let { strategy -> table("strategy") { strategy(strategy) } }
      if (builder.climateLazyBuilder.isInitialized()) {
        table("climate") { climate(builder.climateBuilder.build()) }
      }
      if (builder.heightLazyBuilder.isInitialized()) {
        table("height") { height(builder.heightBuilder.build()) }
      }
      if (builder.noiseLazyBuilder.isInitialized()) {
        table("noise") {
          noise(builder.noiseBuilder.build(), builder.noiseBuilder.hasExplicitBlendWidth)
        }
      }
      if (builder.biomesLazyBuilder.isInitialized()) {
        table("biomes") { selection(builder.biomesBuilder.build()) }
      }
      if (builder.structuresLazyBuilder.isInitialized()) {
        table("structures") { structures(builder.structuresBuilder.build()) }
      }
      if (builder.mobsLazyBuilder.isInitialized()) {
        table("mobs") { selection(builder.mobsBuilder.build()) }
      }
      if (builder.lootLazyBuilder.isInitialized()) {
        table("loot") { selection(builder.lootBuilder.build()) }
      }
    }

    private fun CommentedConfig.strategy(strategy: StrategySettings) {
      when (strategy) {
        is HexStrategy.Builder -> {
          value("type", "hex")
          value("tiling", strategy.tiling)
          optional("ring_region", strategy.ringRegionName)
        }
        is VoronoiStrategy.Builder -> {
          value("type", "voronoi")
          if (strategy.tiling) value("tiling", true)
        }
        is SubdivisionStrategy.Builder -> {
          value("type", "subdivision")
          if (strategy.tiling) value("tiling", true)
        }
        is SurroundStrategy.Builder -> {
          value("type", "surround")
          value("surround_region", strategy.surroundRegionName)
        }
        is ArchipelagoStrategy.Builder -> {
          value("type", "archipelago")
          value("sea_region", strategy.seaRegionName)
        }
        else -> error("Unsupported strategy settings: ${strategy.javaClass.name}")
      }
    }

    private fun CommentedConfig.climate(climate: ClimateConstraints) {
      optional("temperature", climate.temperature?.tomlValue())
      optional("humidity", climate.humidity?.tomlValue())
      optional("continentalness", climate.continentalness?.tomlValue())
      optional("erosion", climate.erosion?.tomlValue())
      optional("depth", climate.depth?.tomlValue())
      optional("weirdness", climate.weirdness?.tomlValue())
      optional("precipitation", climate.precipitation)
      optional("climate_preset", climate.climatePreset)
    }

    private fun CommentedConfig.height(height: HeightConstraints) {
      if (height.maxY == null) {
        value("exact", height.minY)
      } else {
        value("range", listOf(height.minY, height.maxY))
      }
    }

    private fun CommentedConfig.selection(selection: SelectionConstraints) {
      writeSet("allow_mods", selection.allowedMods)
      writeSet("allow_tags", selection.allowedTags)
      writeSet("allow_names", selection.allowedNames)
      writeSet("block_mods", selection.blockedMods)
      writeSet("block_tags", selection.blockedTags)
      writeSet("block_names", selection.blockedNames)
    }

    private fun CommentedConfig.structures(structures: StructureConstraints) {
      structures.selection?.let {
        selection(it)
        if (it.isEmpty()) value("allow_names", emptyList<String>())
      }
      optional("spacing", structures.spacing)
      optional("separation", structures.separation)
      optional("frequency", structures.frequency)
      if (structures.forced.isNotEmpty()) {
        value(
          "force",
          structures.forced.map { structure ->
            inlineTable {
              value("name", structure.name)
              if (structure.radius != null) {
                value("radius", structure.radius)
              } else {
                optional("budget", structure.budget)
              }
            }
          },
        )
      }
    }

    private fun CommentedConfig.noise(
      noise: NoiseConstraints,
      hasExplicitBlendWidth: Boolean,
    ) {
      if (hasExplicitBlendWidth) value("blend_width", noise.blendWidth)
      if (noise.noises.isNotEmpty()) {
        table("noises") {
          for ((name, transform) in noise.noises.toSortedMap()) value(name, transform(transform))
        }
      }
      if (noise.densityFunctions.isNotEmpty()) {
        table("density_functions") {
          for ((name, transform) in noise.densityFunctions.toSortedMap()) {
            value(name, transform(transform))
          }
        }
      }
    }

    private fun transform(transform: NoiseTransform): List<CommentedConfig> =
      transform.operations.map { operation ->
        inlineTable {
          when (operation) {
            is NoiseTransform.Clamp -> {
              value("op", "clamp")
              value("min", operation.min)
              value("max", operation.max)
            }
            is NoiseTransform.Add -> {
              value("op", "add")
              value("value", operation.value)
            }
            is NoiseTransform.Multiply -> {
              value("op", "multiply")
              value("factor", operation.factor)
            }
            is NoiseTransform.Remap -> {
              value("op", "remap")
              value("input_min", operation.inputMin)
              value("input_max", operation.inputMax)
              value("output_min", operation.outputMin)
              value("output_max", operation.outputMax)
            }
            is NoiseTransform.Map -> value("op", operation.type.name.lowercase())
            else -> error("Unsupported noise transform operation: ${operation.javaClass.name}")
          }
        }
      }

    private fun inlineTable(block: CommentedConfig.() -> Unit): CommentedConfig =
      root.createSubConfig().apply(block).also(inlineTables::add)

    private fun CommentedConfig.table(key: String, block: CommentedConfig.() -> Unit) {
      value(key, createSubConfig().apply(block))
    }

    private fun CommentedConfig.value(key: String, value: Any) {
      set<Any>(listOf(key), value)
    }

    private fun CommentedConfig.optional(key: String, value: Any?) {
      if (value != null) value(key, value)
    }

    private fun CommentedConfig.writeSet(key: String, values: Set<String>) {
      if (values.isNotEmpty()) value(key, values.sorted())
    }
  }

  private fun terrasect.definition.ClimateRange.tomlValue(): Any =
    if (hasVariation()) listOf(min, max) else min

  private fun SelectionConstraints.isEmpty(): Boolean =
    allowedMods.isEmpty() &&
      allowedTags.isEmpty() &&
      allowedNames.isEmpty() &&
      blockedMods.isEmpty() &&
      blockedTags.isEmpty() &&
      blockedNames.isEmpty()

  private const val PRESET_COMMENT =
    "Preset file name without .toml. Leave empty to keep Terrasect inactive."
}
