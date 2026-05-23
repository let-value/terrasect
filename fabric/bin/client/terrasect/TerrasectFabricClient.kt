package terrasect

import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.gui.components.debug.DebugScreenEntries
import net.minecraft.resources.Identifier
import terrasect.gui.RegionDebugEntry

object TerrasectFabricClient : ClientModInitializer {
  val REGION_DEBUG: Identifier =
    DebugScreenEntries.register(
      Identifier.fromNamespaceAndPath("terrasect", "region"),
      RegionDebugEntry(),
    )

  override fun onInitializeClient() {}
}
