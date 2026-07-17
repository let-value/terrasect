package terrasect

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.chunk.ChunkAccess
import org.slf4j.LoggerFactory
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry
import terrasect.generation.DimensionContext

// Version- and loader-agnostic body of the server smoke gametest. The registration wrappers differ
// per gametest paradigm (old @GameTest/FabricGameTest vs new GameTestInstance) and per loader
// (Fabric vs NeoForge), but they all force the same preset at mod init and run the same assertion,
// so the actual guard lives here and is shared across every variant.
object ServerSmokeGuard {
  private val log = LoggerFactory.getLogger("ServerSmokeGameTest")

  const val SMOKE_PRESET = "server_smoke_all_constraints"

  const val FORCED_ID = "minecraft:village_plains"

  // Only force the preset when the gametest launch explicitly asks for it, so a preset is never
  // forced onto unrelated runs (e.g. a client-gametest launch that also loads this mod).
  const val FORCE_PROPERTY = "terrasect.serverSmoke"

  // Mirror of the client SmokeGameTest preset: every constraint type on one spawn region so a
  // single world generation exercises noise/climate/height plus mob/loot/structure lookup building.
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
      }
  }

  // Called from a mod initializer: registers the preset and forces it before any world loads, so
  // the server's overworld builds the full Terrasect pipeline. Gated on FORCE_PROPERTY.
  fun installIfRequested() {
    if (System.getProperty(FORCE_PROPERTY).isNullOrBlank()) return
    registerPreset()
    PresetRegistry.forcePresetId = SMOKE_PRESET
    log.info("server smoke: forced preset={}", SMOKE_PRESET)
  }

  // The core guard, shared by every registration variant: the spawn dimension must have a Terrasect
  // context with every constraint type compiled. If a version-specific mixin silently no-ops, the
  // context is absent or its lookups are null and the constraints are inert even though world-gen
  // still "succeeds".
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
      )
    val inactive = status.filterValues { !it }.keys
    check(inactive.isEmpty()) {
      "constraint pipeline not fully applied on $dimensionId: inactive=$inactive status=$status"
    }
    assertCommand(level)
    assertForcedStart(level, context)
    log.info("server smoke: OK — all constraints active on {} status={}", dimensionId, status)
  }

  // The /ts command rides on an ungated Commands constructor mixin; if it silently fails to apply
  // on a version, the command is just absent with no crash, so every version must prove it both
  // registered in the vanilla dispatcher and executes.
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
    log.info("server smoke: /ts locate and /ts query confirmed")
  }

  // Forced placement rides entirely on the chunk-context capture on versions without a dimension
  // key at the createStructures injection site (1.21.1); if that capture silently no-ops, the
  // feature is inert with no crash, so every version must prove the planned start really exists.
  private fun assertForcedStart(level: ServerLevel, context: DimensionContext) {
    val forced = context.forcedStructures!!
    val start = forced.sitesAt(context.traverser, context.cache, 0, 0).single()
    val chunk = getStructureStartsChunk(level, start.site.chunkX, start.site.chunkZ)
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

  private fun getStructureStartsChunk(level: ServerLevel, chunkX: Int, chunkZ: Int): ChunkAccess {
    val statusClass =
      try {
        Class.forName("net.minecraft.world.level.chunk.status.ChunkStatus")
      } catch (_: ClassNotFoundException) {
        Class.forName("net.minecraft.world.level.chunk.ChunkStatus")
      }
    val structureStarts = statusClass.getField("STRUCTURE_STARTS").get(null)
    val getChunk =
      level.javaClass.getMethod(
        "getChunk",
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
        statusClass,
        Boolean::class.javaPrimitiveType,
      )
    return getChunk.invoke(level, chunkX, chunkZ, structureStarts, true) as ChunkAccess
  }
}
