package terrasect

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.levelgen.Heightmap
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertEquals
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.presets.Presets
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

private val LOGGER = LoggerFactory.getLogger("WorldDigestGameTest")

private const val SEED = "seed"
private const val SNAPSHOT = "82d24077c936b73562c54fe4934fbdb0da5af54f549b11bbad94ac4d4a81e8f9"

private val PROBE_LOCATIONS =
    listOf(
        0 to 0,
        512 to 0,
        0 to 512,
        512 to 512,
    )

private val GAME_TEST_SNAPSHOT_BASE: Path by lazy {
  Paths.get("").toAbsolutePath().parent.parent.resolve("test-snapshots")
}

@Suppress("UnstableApiUsage")
private fun computeWorldDigest(
    context: ClientGameTestContext,
    label: String,
    screenshotsDir: Path,
) {
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

    game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          server.playerList.players[0].teleportTo(0.0, 400.0, 0.0)
        }
    )

    context.waitTicks(10)

    context.runOnClient(
        FailableConsumer<Minecraft, Exception> { client ->
          client.player?.let { player ->
            client.player?.xRot = 90f // pitch 90° = looking straight down
            client.player?.yRot = 0f // face north for a consistent orientation
            player.abilities.mayfly = true
            player.abilities.flying = true
            player.onUpdateAbilities()
          }
        }
    )

    game.clientWorld.waitForChunksRender()

    val digest = MessageDigest.getInstance("SHA-256")

    for ((x, z) in PROBE_LOCATIONS) {
      game.server.runOnServer(
          FailableConsumer<MinecraftServer, Exception> { server ->
            server.playerList.players[0].teleportTo(x + 8.0, 120.0, z + 8.0)
          }
      )

      context.waitTicks(60)
      game.clientWorld.waitForChunksDownload()
      game.clientWorld.waitForChunksRender()

      val entries = mutableListOf<String>()
      game.server.runOnServer(
          FailableConsumer<MinecraftServer, Exception> { server ->
            val level = server.overworld()
            for (bx in x until x + 16) {
              for (bz in z until z + 16) {
                val oceanFloor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, bx, bz)
                val worldSurface = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz)
                entries.add("$bx,$bz,$oceanFloor,$worldSurface")
              }
            }
          }
      )

      for (entry in entries) {
        digest.update(entry.toByteArray(Charsets.UTF_8))
      }

      context.takeScreenshot(
          TestScreenshotOptions.of("probe_${x}_${z}").withDestinationDir(screenshotsDir)
      )
    }

    val hex = digest.digest().joinToString("") { "%02x".format(it) }
    LOGGER.info("[WorldDigest] {} digest: {}", label, hex)

    assertEquals(
        SNAPSHOT,
        hex,
        "World digest does not match expected snapshot. This may indicate unintended changes to terrain generation; investigate probe screenshots for details.",
    )
  } finally {
    game.close()
  }
}

@Suppress("UnstableApiUsage")
object VanillaWorldDigestTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    PresetRegistry.forcePresetId = "__disabled__"
    try {
      computeWorldDigest(
          context,
          "vanilla",
          GAME_TEST_SNAPSHOT_BASE.resolve(this::class.simpleName!!),
      )
    } finally {
      PresetRegistry.forcePresetId = null
    }
  }
}

@Suppress("UnstableApiUsage")
object TerrasectWorldDigestTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    PresetRegistry.forcePresetId = Presets.CLIMATE_DEBUG.toString()
    try {
      computeWorldDigest(
          context,
          "terrasect",
          GAME_TEST_SNAPSHOT_BASE.resolve(this::class.simpleName!!),
      )
    } finally {
      PresetRegistry.forcePresetId = null
    }
  }
}
