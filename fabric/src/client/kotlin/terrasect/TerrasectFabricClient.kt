package terrasect

import net.fabricmc.api.ClientModInitializer
//? if >=1.21.11 {
import net.minecraft.client.gui.components.debug.DebugScreenEntries
import net.minecraft.resources.Identifier
import terrasect.gui.RegionDebugEntry
//?}

object TerrasectFabricClient : ClientModInitializer {
  //? if >=1.21.11 {
  val REGION_DEBUG: Identifier =
    DebugScreenEntries.register(
      Identifier.fromNamespaceAndPath("terrasect", "region"),
      RegionDebugEntry(),
    )
  //?}

  override fun onInitializeClient() {}
}
