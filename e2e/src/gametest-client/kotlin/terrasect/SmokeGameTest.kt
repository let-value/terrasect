package terrasect

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.levelgen.Heightmap
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry
import terrasect.generation.DimensionContext

private val log = LoggerFactory.getLogger("SmokeGameTest")

private const val SEED = "terrasect-smoke"
private const val SMOKE_PRESET = "smoke_all_constraints"
private val ALLOWED_BIOMES = setOf("minecraft:desert")

// Applies every constraint type the mod exposes to a single spawn region so one world generation
// exercises the whole pipeline: noise/climate/height terrain shaping plus the mob/loot/structure
// lookup compilation that runs at world load. The biome constraint also proves that the real
// MultiNoiseBiomeSource selection call is filtered, rather than only compiling a lookup.
private fun registerSmokePreset() {
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
        }
        .mobs { blockNames("minecraft:zombie") }
        .loot { blockTags("c:foods") }
        .biomes { allowNames("minecraft:desert") }
    }
}

@Suppress("UnstableApiUsage")
object SmokeGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val originalPresetId = PresetRegistry.forcePresetId
    registerSmokePreset()
    PresetRegistry.forcePresetId = SMOKE_PRESET
    log.info("smoke: creating world preset={} seed={}", SMOKE_PRESET, SEED)

    val game =
      context
        .worldBuilder()
        .setUseConsistentSettings(false)
        .adjustSettings { settings ->
          settings.seed = SEED
          settings.gameMode = WorldCreationUiState.SelectedGameMode.CREATIVE
        }
        .create()

    try {
      context.waitTicks(20)

      val surfaces = ArrayList<Int>(256)
      lateinit var lookupStatus: Map<String, Boolean>
      var dimensionId = ""
      var commandRegistered = false
      var commandResult = 0
      val sampledBiomes = LinkedHashSet<String>()
      val rejectedBiomes = ArrayList<String>()
      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          val level = server.overworld()
          for (bx in 0 until 16) {
            for (bz in 0 until 16) {
              surfaces.add(level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz))
            }
          }
          dimensionId = ResourceKeyCompat.getKeyId(level.dimension())
          val context = DimensionContext.get(dimensionId)
          lookupStatus =
            linkedMapOf(
              "context" to (context != null),
              "noise" to (context?.noiseRegistry != null),
              "structure" to (context?.structureLookup != null),
              "mob" to (context?.mobLookup != null),
              "loot" to (context?.lootLookup != null),
              "biome" to (context?.biomeLookup != null),
            )
          val dispatcher = server.commands.dispatcher
          commandRegistered = dispatcher.root.getChild("ts") != null
          if (commandRegistered) {
            val commandSource = server.createCommandSourceStack()
            commandResult =
              dispatcher.execute("ts locate .overworld_root", commandSource) +
                dispatcher.execute("ts query", commandSource)
          }
          val biomeContext = checkNotNull(context)
          val biomeLookup = checkNotNull(biomeContext.biomeLookup)
          val biomeSource = level.chunkSource.generator.biomeSource
          val sampler = level.chunkSource.randomState().sampler()
          for (qx in 0 until 16) {
            for (qz in 0 until 16) {
              val holder = biomeSource.getNoiseBiome(qx, 0, qz, sampler)
              val id =
                holder
                  .unwrapKey()
                  .map { ResourceKeyCompat.getKeyId(it) }
                  .orElse("unknown")
              sampledBiomes += id
              val region =
                biomeContext.traverser.traverse(qx shl 2, qz shl 2, biomeContext.cache).region
              if (!biomeLookup.isAdmitted(region, holder.value())) {
                rejectedBiomes += id
              }
            }
          }
          log.info(
            "smoke: dim={} surfaces {}..{} avg={} biomes={} rejectedBiomes={} pipeline={}",
            dimensionId,
            surfaces.min(),
            surfaces.max(),
            "%.1f".format(surfaces.average()),
            sampledBiomes,
            rejectedBiomes,
            lookupStatus,
          )
        }
      )

      assertTrue(surfaces.size == 256, "expected 256 spawn columns, read ${surfaces.size}")
      // The core guard: the spawn dimension must have a Terrasect context with every constraint
      // type compiled. If a version-specific mixin silently no-ops, the context is absent or its
      // lookups are null and the constraints are inert even though world-gen still "succeeds".
      assertNotNull(
        DimensionContext.get(dimensionId),
        "no DimensionContext registered for $dimensionId — the ServerLevel mixin did not run, so " +
          "every constraint is inert on this version",
      )
      val inactive = lookupStatus.filterValues { !it }.keys
      assertTrue(
        inactive.isEmpty(),
        "constraint pipeline not fully applied on $dimensionId: inactive=$inactive status=$lookupStatus",
      )
      assertTrue(
        commandRegistered,
        "'/ts' is not in the vanilla dispatcher — the Commands mixin did not run on this version",
      )
      assertTrue(
        commandResult == 2,
        "'/ts locate .overworld_root' or '/ts query' failed on $dimensionId",
      )
      assertTrue(
        sampledBiomes.isNotEmpty() && sampledBiomes.all(ALLOWED_BIOMES::contains),
        "biome constraint admitted only desert but sampled $sampledBiomes on $dimensionId",
      )
      assertTrue(
        rejectedBiomes.isEmpty(),
        "generated biome holders were rejected by their region lookup on $dimensionId: $rejectedBiomes",
      )
      log.info("smoke: OK — all constraints active on $dimensionId")
    } finally {
      game.close()
      PresetRegistry.forcePresetId = originalPresetId
      PresetRegistry.presets.remove(SMOKE_PRESET)
    }
  }
}
