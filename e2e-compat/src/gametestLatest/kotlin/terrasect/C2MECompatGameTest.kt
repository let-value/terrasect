package terrasect

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.chunk.status.ChunkStatus
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry

private val log = LoggerFactory.getLogger("C2MECompatGameTest")

private const val SEED = "terrasect-c2me-compat"
private const val COMPAT_PRESET = "c2me_compat_full_constraints"
private const val RADIUS_CHUNKS = 12

// Full constraint set, same shape as the smoke preset — C2ME's compat risk is about *concurrent*
// access to this pipeline's shared state (DimensionContext, compiled lookups, region traversal
// caches), so every lookup path needs to be live, not just one.
private fun registerCompatPreset() {
  PresetRegistry.presets[COMPAT_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root")
        .climate {
          temperature(-200, 400)
          humidity(0, 800)
        }
        .structures {
          spacing(16)
          separation(8)
        }
        .mobs { blockNames("minecraft:zombie") }
        .loot { blockTags("c:foods") }
    }
}

// C2ME rewrites chunk generation/loading to run on a pool of worker threads instead of vanilla's
// single generation thread. Terrasect's own generation-time state (DimensionContext lookups,
// RegionRegistry traversal, per-chunk context caching) was written against that single-threaded
// assumption, so this is the one compat mod that can surface real concurrency bugs — corrupted
// shared caches, races on lazily-initialized fields — that none of the other (single-threaded)
// compat tests would ever exercise.
// Evidence: a wide ring of chunks requested concurrently (mirroring C2ME's own async chunk
// pipeline, not a hand-rolled thread pool) all complete successfully with no chunk-load errors.
@Suppress("UnstableApiUsage")
object C2MECompatGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val originalPresetId = PresetRegistry.forcePresetId
    registerCompatPreset()
    PresetRegistry.forcePresetId = COMPAT_PRESET

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

      var requested = 0
      val errors = java.util.concurrent.ConcurrentLinkedQueue<String>()
      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          val level = server.overworld()
          // Fire every chunk request before awaiting any of them, so they queue up together and
          // C2ME's scheduler actually parallelizes them rather than serializing on the main thread.
          val futures =
            (-RADIUS_CHUNKS..RADIUS_CHUNKS).flatMap { cx ->
              (-RADIUS_CHUNKS..RADIUS_CHUNKS).map { cz ->
                requested++
                level.chunkSource.getChunkFuture(cx, cz, ChunkStatus.FULL, true).thenAccept { result
                  ->
                  if (!result.isSuccess) {
                    errors.add("chunk ($cx,$cz): ${result.error}")
                  }
                }
              }
            }
          java.util.concurrent.CompletableFuture.allOf(*futures.toTypedArray()).join()
          log.info("c2me compat: requested={} errors={}", requested, errors.size)
        }
      )

      assertTrue(
        errors.isEmpty(),
        "c2me compat: ${errors.size}/$requested chunk loads failed under concurrent generation " +
          "with the full Terrasect constraint pipeline active: ${errors.take(10)}",
      )
      log.info("c2me compat: OK — {} chunks loaded concurrently with no errors", requested)
    } finally {
      game.close()
      PresetRegistry.forcePresetId = originalPresetId
      PresetRegistry.presets.remove(COMPAT_PRESET)
    }
  }
}
