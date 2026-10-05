package terrasect.gametest

import java.nio.file.Path
import net.minecraft.server.level.ServerLevel
import org.slf4j.LoggerFactory
import terrasect.compat.ResourceKeyCompat
import terrasect.config.TerrasectConfigManager
import terrasect.config.TerrasectToml
import terrasect.config.TerrasectTomlWriter
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry
import terrasect.generation.DimensionContext

object ServerSmokeGuard {
  private val log = LoggerFactory.getLogger("ServerSmokeGameTest")

  const val SMOKE_PRESET = "server_smoke_all_constraints"

  const val FORCED_ID = "minecraft:village_plains"

  const val FORCE_PROPERTY = "terrasect.serverSmoke"

  fun registerPreset() {
    PresetRegistry.presets[SMOKE_PRESET] =
      RegionRegistry().apply {
        setRoot("minecraft:overworld", "overworld_root")
        region("overworld_root")
          .climate {
            temperature(-200, 400)
            humidity(0, 800)
            precipitation("rain")
          }
          .height { range(60, 200) }
          .noise {
            densityFunction("continents") {
              it.multiply(0.0)
              it.add(0.2)
            }
            densityFunction("erosion") {
              it.multiply(0.0)
              it.add(0.2)
            }
          }
          .structures {
            allowMods("minecraft")
            spacing(24)
            separation(8)
            force(FORCED_ID)
          }
          .mobs { blockNames("minecraft:zombie") }
          .loot { blockTags("c:foods") }
          .biomes { allowNames("minecraft:desert") }
      }
  }

  fun installIfRequested() {
    if (System.getProperty(FORCE_PROPERTY).isNullOrBlank()) return
    registerPreset()
    PresetRegistry.forcePresetId = SMOKE_PRESET
    log.info("server smoke: forced preset={}", SMOKE_PRESET)
  }

  fun assertPipeline(level: ServerLevel) {
    val dimensionId = ResourceKeyCompat.getKeyId(level.dimension())
    val context =
      DimensionContext.get(dimensionId)
        ?: error(
          "no DimensionContext registered for $dimensionId — the ServerLevel mixin did not run, so " +
            "every constraint is inert on this version"
        )
    val status =
      linkedMapOf(
        "noise" to (context.noiseRegistry != null),
        "structure" to (context.structureLookup != null),
        "forced" to (context.forcedStructures != null),
        "mob" to (context.mobLookup != null),
        "loot" to (context.lootLookup != null),
        "biome" to (context.biomeLookup != null),
      )
    val inactive = status.filterValues { !it }.keys
    check(inactive.isEmpty()) {
      "constraint pipeline not fully applied on $dimensionId: inactive=$inactive status=$status"
    }
    assertCommand(level)
    assertForcedStart(level, context)
    assertBiomeConstraint(level, context)
    log.info("server smoke: OK — all constraints active on {} status={}", dimensionId, status)
  }

  @JvmStatic
  fun assertConfiguration(configRoot: Path) {
    val loaded = TerrasectConfigManager.initialize(configRoot)
    check(loaded.createdFiles.isEmpty()) { "startup did not create the default configuration" }
    check(loaded.presets.keys.containsAll(listOf("example", "climate_debug"))) {
      "default presets were not loaded: ${loaded.presets.keys}"
    }
    check(TerrasectToml.parseConfig(TerrasectTomlWriter.write(loaded.config)) == loaded.config) {
      "configuration TOML round trip failed"
    }
    for ((name, preset) in loaded.presets) {
      val exported = TerrasectTomlWriter.write(preset)
      val parsed = TerrasectToml.parsePreset(exported, "$name-export.toml")
      check(parsed.drafts.keys == preset.drafts.keys) { "$name preset TOML round trip failed" }
    }
    for (name in listOf("core.CommentedConfig", "toml.TomlParser", "toml.TomlWriter")) {
      val type = Class.forName("com.electronwill.nightconfig.$name")
      log.info(
        "Night Config {} module={} source={}",
        name,
        type.module.name,
        type.protectionDomain.codeSource?.location,
      )
    }
    log.info("configuration smoke: OK — existing config, presets and TOML export")
  }

  private fun assertBiomeConstraint(level: ServerLevel, context: DimensionContext) {
    val lookup = context.biomeLookup!!
    val allowed = setOf("minecraft:desert")
    val sampled = LinkedHashSet<String>()
    val biomeSource = level.chunkSource.generator.biomeSource
    val sampler = level.chunkSource.randomState().sampler()
    for (qx in 0 until 16) {
      for (qz in 0 until 16) {
        val holder = biomeSource.getNoiseBiome(qx, 0, qz, sampler)
        val id = holder.unwrapKey().map { ResourceKeyCompat.getKeyId(it) }.orElse("unknown")
        sampled += id
        val region = context.traverser.traverse(qx shl 2, qz shl 2, context.cache).region
        check(lookup.isAdmitted(region, holder.value())) {
          "source returned biome $id rejected by its region lookup at quart=($qx,$qz)"
        }
      }
    }
    check(sampled.isNotEmpty() && sampled.all(allowed::contains)) {
      "biome constraint admitted only desert but sampled $sampled"
    }
    log.info("server smoke: biome constraint confirmed — sampled={}", sampled)
  }

  private fun assertCommand(level: ServerLevel) {
    val server = level.server
    val dispatcher = server.commands.dispatcher
    checkNotNull(dispatcher.root.getChild("ts")) {
      "'/ts' is not in the vanilla dispatcher — the Commands mixin did not run on this version"
    }
    val source = server.createCommandSourceStack()
    check(dispatcher.execute("ts locate .overworld_root", source) == 1) {
      "'/ts locate .overworld_root' failed"
    }
    check(dispatcher.execute("ts query", source) == 1) { "'/ts query' failed" }
    check(dispatcher.execute("ts print .overworld_root", source) == 1) { "'/ts print' failed" }
    log.info("server smoke: /ts locate and /ts query confirmed")
  }

  private fun assertForcedStart(level: ServerLevel, context: DimensionContext) {
    val forced = context.forcedStructures!!
    val start = forced.sitesAt(context.traverser, context.cache, 0, 0).single()
    val chunk = level.getChunk(start.site.chunkX, start.site.chunkZ)
    val structureStart = chunk.getStartForStructure(start.entry.holder.value())
    check(structureStart != null && structureStart.isValid) {
      "forced ${start.entry.id} StructureStart missing at its planned chunk " +
        "(${start.site.chunkX},${start.site.chunkZ}) — forced placement is inert on this version"
    }
    log.info(
      "server smoke: forced {} start confirmed at chunk ({},{})",
      start.entry.id,
      start.site.chunkX,
      start.site.chunkZ,
    )
  }

  @JvmStatic
  fun run(server: net.minecraft.server.MinecraftServer) {
    try {
      assertPipeline(server.overworld())
      server.halt(false)
    } catch (failure: Throwable) {
      log.error("Server smoke failed", failure)
      Runtime.getRuntime().halt(1)
    }
  }
}
