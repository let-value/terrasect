package terrasect.config

import com.electronwill.nightconfig.core.UnmodifiableConfig
import com.electronwill.nightconfig.toml.TomlParser
import terrasect.definition.RegionBuilder
import terrasect.definition.RegionRegistry
import terrasect.definition.SelectionConstraints
import terrasect.definition.Strategy
import terrasect.definition.StructureConstraints
import terrasect.helpers.NoiseTransform
import terrasect.instrumentation.TerrasectInstrScope
import terrasect.instrumentation.TerrasectMetricEvent
import terrasect.sdf.Decoration
import terrasect.sdf.SiteMetric

class TerrasectConfigException(message: String, cause: Throwable? = null) :
  IllegalArgumentException(message, cause)

object TerrasectToml {
  internal const val SCHEMA_VERSION = 1L

  fun parseConfig(input: String, source: String = "config.toml"): TerrasectConfig {
    val root = parse(input, source)
    root.rejectUnknown("preset", "logging", "instrumentation")
    val logging = parseLogging(root.table("logging"))
    val instrumentation = parseInstrumentation(root.table("instrumentation"))
    return TerrasectConfig(
      preset = root.string("preset", allowBlank = true)?.trim()?.takeIf(String::isNotEmpty),
      logging = logging,
      instrumentation = instrumentation,
    )
  }

  fun parsePreset(
    input: String,
    source: String = "preset.toml",
    warning: (String) -> Unit = {},
  ): RegionRegistry {
    val root = parse(input, source)
    root.rejectUnknown("schema", "roots", "regions")
    val schema = root.requiredLong("schema")
    if (schema != SCHEMA_VERSION) {
      root.fail("schema", "unsupported schema $schema, expected $SCHEMA_VERSION")
    }

    val roots = root.requiredTable("roots")
    val regions = root.requiredTable("regions")
    val regionTables =
      regions.entries().associate { (name, value) ->
        name to Table(regions.asTable(name, value), "${regions.path}.$name")
      }
    if (regionTables.isEmpty()) regions.fail("at least one region is required")
    if (regionTables.size > 255) regions.fail("at most 255 regions are supported")

    val parents = regionTables.mapValues { (_, table) ->
      table.rejectUnknown(
        "parent",
        "radius",
        "budget",
        "origin_anchor",
        "strategy",
        "decorations",
        "climate",
        "height",
        "noise",
        "biomes",
        "structures",
        "mobs",
        "loot",
      )
      table.string("parent")
    }
    validateReferences(roots, regionTables, parents)

    val registry = RegionRegistry()
    regionTables.keys.sorted().forEach(registry::region)
    for (name in regionTables.keys.sorted()) {
      applyRegion(registry.region(name), regionTables.getValue(name), regionTables.keys, warning)
    }
    for ((name, parent) in parents.toSortedMap()) {
      if (parent != null) registry.region(name).parent(parent)
    }
    for ((dimension, value) in roots.entries().sortedBy { it.first }) {
      registry.setRoot(dimension, roots.asString(dimension, value))
    }
    return registry
  }

  private fun parse(input: String, source: String): Table {
    val config =
      try {
        TomlParser().parse(if (input.endsWith('\n')) input else "$input\n")
      } catch (error: Exception) {
        throw TerrasectConfigException("$source: invalid TOML: ${error.message}", error)
      }
    return Table(config, source)
  }

  private fun parseLogging(table: Table?): LoggingConfig {
    if (table == null) return LoggingConfig()
    table.rejectUnknown("load_summary", "validation_warnings", "registry_debug")
    return LoggingConfig(
      loadSummary = table.boolean("load_summary") ?: true,
      validationWarnings = table.boolean("validation_warnings") ?: true,
      registryDebug = table.boolean("registry_debug") ?: false,
    )
  }

  private fun parseInstrumentation(table: Table?): InstrumentationConfig {
    if (table == null) return InstrumentationConfig()
    table.rejectUnknown("enabled", "counters", "timers", "scopes", "events")
    val scopes = mutableMapOf<TerrasectInstrScope, InstrumentationScopeConfig>()
    table.table("scopes")?.let { scopesTable ->
      val known = TerrasectInstrScope.entries.associateBy { it.id }
      for ((id, value) in scopesTable.entries()) {
        val scope = known[id] ?: scopesTable.fail(id, "unknown instrumentation scope")
        scopes[scope] =
          when (value) {
            is Boolean -> InstrumentationScopeConfig(enabled = value)
            is UnmodifiableConfig -> {
              val options = Table(value, "${scopesTable.path}.$id")
              options.rejectUnknown("enabled", "counters", "timers")
              InstrumentationScopeConfig(
                enabled = options.boolean("enabled"),
                counters = options.boolean("counters"),
                timers = options.boolean("timers"),
              )
            }
            else -> scopesTable.fail(id, "expected a boolean or table")
          }
      }
    }

    val events = mutableMapOf<TerrasectMetricEvent, Boolean>()
    table.table("events")?.let { eventsTable ->
      val known = TerrasectMetricEvent.entries.associateBy { it.id }
      for ((id, value) in eventsTable.entries()) {
        val event = known[id] ?: eventsTable.fail(id, "unknown instrumentation event")
        events[event] = eventsTable.asBoolean(id, value)
      }
    }
    return InstrumentationConfig(
      enabled = table.boolean("enabled") ?: false,
      counters = table.boolean("counters") ?: false,
      timers = table.boolean("timers") ?: false,
      scopes = scopes,
      events = events,
    )
  }

  private fun validateReferences(
    roots: Table,
    regions: Map<String, Table>,
    parents: Map<String, String?>,
  ) {
    for ((dimension, value) in roots.entries()) {
      val name = roots.asString(dimension, value)
      if (name !in regions) roots.fail(dimension, "unknown root region '$name'")
    }
    for ((name, parent) in parents) {
      if (parent != null && parent !in regions) {
        regions.getValue(name).fail("parent", "unknown region '$parent'")
      }
    }

    val visited = mutableSetOf<String>()
    val visiting = mutableSetOf<String>()
    fun visit(name: String) {
      if (name in visited) return
      if (!visiting.add(name)) regions.getValue(name).fail("parent", "region parent cycle")
      parents[name]?.let(::visit)
      visiting.remove(name)
      visited.add(name)
    }
    regions.keys.forEach(::visit)
  }

  private fun applyRegion(
    builder: RegionBuilder,
    table: Table,
    regionNames: Set<String>,
    warning: (String) -> Unit,
  ) {
    val radius = table.long("radius")
    val budget = table.long("budget")
    if (radius != null && budget != null) table.fail("radius", "cannot be combined with budget")
    radius?.let(builder::radius)
    budget?.let(builder::budget)
    if (table.boolean("origin_anchor") == true) {
      builder.originAnchor()
      warning("${table.path}.origin_anchor is accepted but currently has no runtime effect")
    }
    table.table("strategy")?.let { applyStrategy(builder, it, regionNames) }
    table.raw("decorations")?.let { applyDecorations(builder, table, it) }
    table.table("climate")?.let { applyClimate(builder, it, warning) }
    table.table("height")?.let {
      warning("${it.path} is accepted but currently has no runtime effect")
      applyHeight(builder, it)
    }
    table.table("noise")?.let { applyNoise(builder, it) }
    table.table("biomes")?.let {
      warning("${it.path} is accepted but currently has no runtime effect")
      applySelection(builder.biomesBuilder, it)
    }
    table.table("structures")?.let { applyStructures(builder.structuresBuilder, it) }
    table.table("mobs")?.let { applySelection(builder.mobsBuilder, it) }
    table.table("loot")?.let { applySelection(builder.lootBuilder, it) }
  }

  private fun applyStrategy(builder: RegionBuilder, table: Table, regionNames: Set<String>) {
    val type = table.requiredString("type").lowercase()
    when (type) {
      "hex" -> {
        table.rejectUnknown("type", "tiling", "ring_region", "rounding")
        val ring = table.string("ring_region")
        if (ring != null && ring !in regionNames)
          table.fail("ring_region", "unknown region '$ring'")
        val hex = Strategy.hex(ring).tiling(table.boolean("tiling") ?: true)
        table.float("rounding")?.let(hex::rounding)
        builder.strategy(hex)
      }
      "voronoi" -> {
        table.rejectUnknown("type", "tiling", "metric")
        val voronoi = Strategy.voronoi().tiling(table.boolean("tiling") ?: false)
        table.string("metric")?.let { name ->
          val metric =
            SiteMetric.entries.find { it.name.equals(name, ignoreCase = true) }
              ?: table.fail("metric", "unknown metric '$name'")
          voronoi.metric(metric)
        }
        builder.strategy(voronoi)
      }
      "subdivision" -> {
        table.rejectUnknown("type", "tiling")
        builder.strategy(Strategy.subdivision().tiling(table.boolean("tiling") ?: false))
      }
      "archipelago" -> {
        table.rejectUnknown("type", "sea_region")
        val sea = table.requiredString("sea_region")
        if (sea !in regionNames) {
          table.fail("sea_region", "unknown region '$sea'")
        }
        builder.strategy(Strategy.archipelago(sea))
      }
      "surround" -> {
        table.rejectUnknown("type", "surround_region")
        val surround = table.requiredString("surround_region")
        if (surround !in regionNames) {
          table.fail("surround_region", "unknown region '$surround'")
        }
        builder.strategy(Strategy.surround(surround))
      }
      else -> table.fail("type", "unknown strategy '$type'")
    }
  }

  private fun applyDecorations(builder: RegionBuilder, table: Table, raw: Any?) {
    for ((index, config) in table.asTableList("decorations", raw).withIndex()) {
      val entry = Table(config, "${table.path}.decorations[$index]")
      val type = entry.requiredString("type").lowercase()
      val decoration =
        when (type) {
          "warp" -> {
            entry.rejectUnknown("type", "amplitude", "scale", "octaves")
            Decoration.warp(
              entry.requiredFloat("amplitude"),
              entry.requiredFloat("scale"),
              entry.int("octaves") ?: 2,
            )
          }
          "dither" -> {
            entry.rejectUnknown("type", "width", "scale")
            Decoration.dither(entry.requiredFloat("width"), entry.float("scale") ?: 8f)
          }
          "swirl" -> {
            entry.rejectUnknown("type", "strength", "radius")
            Decoration.swirl(entry.requiredFloat("strength"), entry.requiredFloat("radius"))
          }
          "ripple" -> {
            entry.rejectUnknown("type", "amplitude", "wavelength")
            Decoration.ripple(entry.requiredFloat("amplitude"), entry.requiredFloat("wavelength"))
          }
          "shear" -> {
            entry.rejectUnknown("type", "x", "z")
            Decoration.shear(entry.float("x") ?: 0f, entry.float("z") ?: 0f)
          }
          "terrace" -> {
            entry.rejectUnknown("type", "step")
            Decoration.terrace(entry.requiredFloat("step"))
          }
          "gap" -> {
            entry.rejectUnknown("type", "width")
            Decoration.gap(entry.requiredFloat("width"))
          }
          "onion" -> {
            entry.rejectUnknown("type", "thickness")
            Decoration.onion(entry.requiredFloat("thickness"))
          }
          "stripes" -> {
            entry.rejectUnknown("type", "width", "gap", "angle")
            Decoration.stripes(
              entry.requiredFloat("width"),
              entry.requiredFloat("gap"),
              entry.float("angle") ?: 0f,
            )
          }
          "rings" -> {
            entry.rejectUnknown("type", "width", "gap")
            Decoration.rings(entry.requiredFloat("width"), entry.requiredFloat("gap"))
          }
          else -> entry.fail("type", "unknown decoration '$type'")
        }
      builder.decoration(decoration)
    }
  }

  private fun applyClimate(builder: RegionBuilder, table: Table, warning: (String) -> Unit) {
    table.rejectUnknown(
      "temperature",
      "humidity",
      "continentalness",
      "erosion",
      "depth",
      "weirdness",
      "precipitation",
      "climate_preset",
    )
    if (table.raw("precipitation") != null) {
      warning("${table.path}.precipitation is accepted but currently has no runtime effect")
    }
    if (table.raw("climate_preset") != null) {
      warning("${table.path}.climate_preset is accepted but currently has no runtime effect")
    }
    builder.climate {
      table.longRange("temperature")?.applyTo({ temperature(it) }) { min, max ->
        temperature(min, max)
      }
      table.longRange("humidity")?.applyTo({ humidity(it) }) { min, max -> humidity(min, max) }
      table.longRange("continentalness")?.applyTo({ continentalness(it) }) { min, max ->
        continentalness(min, max)
      }
      table.longRange("erosion")?.applyTo({ erosion(it) }) { min, max -> erosion(min, max) }
      table.longRange("depth")?.applyTo({ depth(it) }) { min, max -> depth(min, max) }
      table.longRange("weirdness")?.applyTo({ weirdness(it) }) { min, max -> weirdness(min, max) }
      table.string("precipitation")?.let(::precipitation)
      table.string("climate_preset")?.let(::climatePreset)
    }
  }

  private fun applyHeight(builder: RegionBuilder, table: Table) {
    table.rejectUnknown("exact", "range")
    val exactValue = table.int("exact")
    val rangeValue = table.intPair("range")
    if ((exactValue == null) == (rangeValue == null)) {
      table.fail("exactly one of exact or range is required")
    }
    builder.height {
      if (exactValue != null) exact(exactValue) else range(rangeValue!!.first, rangeValue.second)
    }
  }

  private fun applyNoise(builder: RegionBuilder, table: Table) {
    table.rejectUnknown("blend_width", "noises", "density_functions")
    builder.noise {
      table.float("blend_width")?.let(::blendWidth)
      table.table("noises")?.let { entries ->
        for ((name, value) in entries.entries().sortedBy { it.first }) {
          noise(name, parseTransform(entries, name, value))
        }
      }
      table.table("density_functions")?.let { entries ->
        for ((name, value) in entries.entries().sortedBy { it.first }) {
          densityFunction(name, parseTransform(entries, name, value))
        }
      }
    }
  }

  private fun parseTransform(owner: Table, name: String, value: Any?): NoiseTransform {
    val operations = owner.asTableList(name, value)
    val builder = NoiseTransform.builder()
    for ((index, config) in operations.withIndex()) {
      val table = Table(config, "${owner.path}.$name[$index]")
      when (val op = table.requiredString("op").lowercase()) {
        "clamp" -> {
          table.rejectUnknown("op", "min", "max")
          val min = table.requiredDouble("min")
          val max = table.requiredDouble("max")
          if (min > max) table.fail("min", "must not exceed max")
          builder.clamp(min, max)
        }
        "add" -> {
          table.rejectUnknown("op", "value")
          builder.add(table.requiredDouble("value"))
        }
        "multiply" -> {
          table.rejectUnknown("op", "factor")
          builder.multiply(table.requiredDouble("factor"))
        }
        "remap" -> {
          table.rejectUnknown("op", "input_min", "input_max", "output_min", "output_max")
          builder.remap(
            table.requiredDouble("input_min"),
            table.requiredDouble("input_max"),
            table.requiredDouble("output_min"),
            table.requiredDouble("output_max"),
          )
        }
        "map" -> {
          table.rejectUnknown("op", "type")
          builder.map(table.mapType("type"))
        }
        "abs",
        "square",
        "cube",
        "half_negative",
        "quarter_negative",
        "invert",
        "squeeze" -> {
          table.rejectUnknown("op")
          builder.map(NoiseTransform.MapType.valueOf(op.uppercase()))
        }
        else -> table.fail("op", "unknown noise operation '$op'")
      }
    }
    return builder.build()
  }

  private fun applySelection(builder: SelectionConstraints.Builder, table: Table) {
    val selection = parseSelection(table)
    selection.allowMods?.let { builder.allowMods(*it) }
    selection.allowTags?.let { builder.allowTags(*it) }
    selection.allowNames?.let { builder.allowNames(*it) }
    selection.blockMods?.let { builder.blockMods(*it) }
    selection.blockTags?.let { builder.blockTags(*it) }
    selection.blockNames?.let { builder.blockNames(*it) }
  }

  private fun applyStructures(builder: StructureConstraints.Builder, table: Table) {
    table.rejectUnknown(*(SELECTION_KEYS + arrayOf("spacing", "separation", "frequency", "force")))
    val selection = parseSelection(table, rejectUnknown = false)
    selection.allowMods?.let { builder.allowMods(*it) }
    selection.allowTags?.let { builder.allowTags(*it) }
    selection.allowNames?.let { builder.allowNames(*it) }
    selection.blockMods?.let { builder.blockMods(*it) }
    selection.blockTags?.let { builder.blockTags(*it) }
    selection.blockNames?.let { builder.blockNames(*it) }
    table.int("spacing")?.let(builder::spacing)
    table.int("separation")?.let(builder::separation)
    table.float("frequency")?.let(builder::frequency)
    table.raw("force")?.let { raw ->
      for ((index, config) in table.asTableList("force", raw).withIndex()) {
        val forced = Table(config, "${table.path}.force[$index]")
        forced.rejectUnknown("name", "budget", "radius")
        val name = forced.requiredString("name")
        val budget = forced.long("budget")
        val radius = forced.long("radius")
        if (budget != null && radius != null) {
          forced.fail("budget", "cannot be combined with radius")
        }
        when {
          budget != null -> builder.force(name, budget)
          radius != null -> builder.forceRadius(name, radius)
          else -> builder.force(name)
        }
      }
    }
  }

  private fun parseSelection(table: Table, rejectUnknown: Boolean = true): SelectionSpec {
    if (rejectUnknown) table.rejectUnknown(*SELECTION_KEYS)
    return SelectionSpec(
      allowMods = table.stringArray("allow_mods"),
      allowTags = table.stringArray("allow_tags"),
      allowNames = table.stringArray("allow_names"),
      blockMods = table.stringArray("block_mods"),
      blockTags = table.stringArray("block_tags"),
      blockNames = table.stringArray("block_names"),
    )
  }

  private val SELECTION_KEYS =
    arrayOf("allow_mods", "allow_tags", "allow_names", "block_mods", "block_tags", "block_names")
}

private data class SelectionSpec(
  val allowMods: Array<String>?,
  val allowTags: Array<String>?,
  val allowNames: Array<String>?,
  val blockMods: Array<String>?,
  val blockTags: Array<String>?,
  val blockNames: Array<String>?,
)
