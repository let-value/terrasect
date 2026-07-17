package terrasect

import net.fabricmc.api.ClientModInitializer
import terrasect.gui.RegionDebugEntry

object TerrasectFabricClient : ClientModInitializer {
  override fun onInitializeClient() {
    RegionDebugEntry.register()
  }
}
