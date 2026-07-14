package terrasect

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("DistantHorizonsCompatGameTest")

private const val SEED = "terrasect-dh-compat"

// Distant Horizons doesn't add world-gen content the way BOP does — its unique surface area with
// Terrasect is that it runs its own LOD (level-of-detail) chunk generation pipeline against the
// same ChunkGenerator/BiomeSource Terrasect's mixins modify, off the main generation thread. The
// compat risk is DH's batch LOD generator silently failing against a modified generator (no
// crash, just an empty/never-created LOD store).
// Evidence: DH actually creates its per-dimension LOD database file for the overworld, proving
// its generation pipeline engaged with Terrasect's generator rather than erroring out silently.
@Suppress("UnstableApiUsage")
object DistantHorizonsCompatGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val game =
      context
        .worldBuilder()
        .setUseConsistentSettings(false)
        .adjustSettings { settings ->
          settings.seed = SEED
          settings.gameMode = WorldCreationUiState.SelectedGameMode.CREATIVE
        }
        .create()

    var dbPath: java.nio.file.Path? = null
    try {
      // Give DH's background LOD batch generator time to run at least one pass.
      context.waitTicks(200)

      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          val level = server.overworld()
          val dimId = level.dimension().identifier()
          dbPath =
            server
              .getWorldPath(LevelResource.ROOT)
              .resolve("dimensions")
              .resolve(dimId.namespace)
              .resolve(dimId.path)
              .resolve("data")
              .resolve("DistantHorizons.sqlite")
          log.info("dh compat: expecting LOD database at {}", dbPath)
        }
      )
    } finally {
      game.close()
    }

    val path = dbPath
    assertTrue(
      path != null && java.nio.file.Files.exists(path),
      "Distant Horizons never created its LOD database at $dbPath — its LOD generation pipeline " +
        "may be failing silently against Terrasect's chunk generator",
    )
    log.info("dh compat: OK — LOD database present at {}", path)
  }
}
