package terrasect

import net.fabricmc.api.ClientModInitializer

object TerrasectFabricClient : ClientModInitializer {
  override fun onInitializeClient() {
    // This entrypoint is suitable for setting up client-specific logic, such as rendering.
    Terrasect.initClient()
  }
}
