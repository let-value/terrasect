package terrasect.gametest

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.server.MinecraftServer
import org.apache.commons.lang3.function.FailableConsumer

fun closeCompatWorld(game: TestSingleplayerContext) {
  game.server.runOnServer(FailableConsumer<MinecraftServer, Exception> { it.halt(false) })
  game.close()
}
