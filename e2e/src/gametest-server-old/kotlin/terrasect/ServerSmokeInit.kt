package terrasect.gametest

import net.fabricmc.api.ModInitializer

class ServerSmokeInit : ModInitializer {
  override fun onInitialize() {
    ServerSmokeGuard.installIfRequested()
  }
}
