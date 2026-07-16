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

// Applies every constraint type the mod exposes to a single spawn region so one world generation
// exercises the whole pipeline: noise/climate/height terrain shaping plus the mob/loot/structure
// lookup compilation that runs at world load.
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
            )
          log.info(
            "smoke: dim={} surfaces {}..{} avg={} pipeline={}",
            dimensionId,
            surfaces.min(),
            surfaces.max(),
            "%.1f".format(surfaces.average()),
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
      log.info("smoke: OK — all constraints active on $dimensionId")
    } finally {
      game.close()
      PresetRegistry.forcePresetId = originalPresetId
      PresetRegistry.presets.remove(SMOKE_PRESET)
    }
  }
}
