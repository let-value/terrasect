package terrasect

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
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

private val log = LoggerFactory.getLogger("CompatSmokeGameTest")

private const val SEED = "terrasect-compat-smoke"
private const val SMOKE_PRESET = "compat_smoke_all_constraints"

// Third-party mods present on every supported version's e2e-compat build (see
// build.e2e-compat.gradle.kts). If one silently fails to load, this whole test run would still
// "pass" against a vanilla-only environment without anyone noticing — assert their presence so a
// broken compat dependency fails loudly instead of quietly narrowing coverage.
// C2ME is deliberately NOT in this list: its 1.21.11 build has a jar-in-jar (c2me-base) that
// fails to auto-extract under the Fabric Loader version pinned there, unrelated to Terrasect —
// it's latest-only for now, and its own C2MECompatGameTest (also latest-only) covers it.
private val COMPAT_MOD_IDS = listOf("biomesoplenty", "terrablender", "distanthorizons")

// Same constraint set as e2e's SmokeGameTest (every constraint type on one region), run here
// with the compat mods present to prove the full pipeline still activates when they're loaded —
// distinct from e2e's own SmokeGameTest, which must keep passing with zero third-party mods.
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
          spacing(24)
          separation(8)
        }
        .mobs { blockNames("minecraft:zombie") }
        .loot { blockTags("c:foods") }
    }
}

@Suppress("UnstableApiUsage")
object CompatSmokeGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val modStatus = COMPAT_MOD_IDS.associateWith { FabricLoader.getInstance().isModLoaded(it) }
    log.info("compat smoke: compat mods {}", modStatus)
    assertTrue(
      modStatus.values.all { it },
      "compat mods missing from the e2e-compat game, breaking cross-mod coverage: " +
        modStatus.filterValues { !it }.keys,
    )

    val originalPresetId = PresetRegistry.forcePresetId
    registerSmokePreset()
    PresetRegistry.forcePresetId = SMOKE_PRESET
    log.info("compat smoke: creating world preset={} seed={}", SMOKE_PRESET, SEED)

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
            "compat smoke: dim={} surfaces {}..{} avg={} pipeline={}",
            dimensionId,
            surfaces.min(),
            surfaces.max(),
            "%.1f".format(surfaces.average()),
            lookupStatus,
          )
        }
      )

      assertTrue(surfaces.size == 256, "expected 256 spawn columns, read ${surfaces.size}")
      assertNotNull(
        DimensionContext.get(dimensionId),
        "no DimensionContext registered for $dimensionId with compat mods present — the ServerLevel " +
          "mixin did not run, so every constraint is inert",
      )
      val inactive = lookupStatus.filterValues { !it }.keys
      assertTrue(
        inactive.isEmpty(),
        "constraint pipeline not fully applied on $dimensionId with compat mods present: " +
          "inactive=$inactive status=$lookupStatus",
      )
      log.info("compat smoke: OK — all constraints active on $dimensionId with compat mods loaded")
    } finally {
      game.close()
      PresetRegistry.forcePresetId = originalPresetId
      PresetRegistry.presets.remove(SMOKE_PRESET)
    }
  }
}
