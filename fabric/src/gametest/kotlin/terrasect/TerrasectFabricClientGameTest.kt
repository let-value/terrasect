package terrasect

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState

@Suppress("UnstableApiUsage")
object TerrasectFabricClientGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    val game =
        context
            .worldBuilder()
            .setUseConsistentSettings(false)
            .adjustSettings { settings ->
              settings.seed = "seed"
              settings.gameMode = WorldCreationUiState.SelectedGameMode.CREATIVE
            }
            .create()

    game.clientWorld.waitForChunksRender()

    context.takeScreenshot("spawn")

    val cache = Terrasect.cache

    game.close()
  }
}
