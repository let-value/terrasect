package terrasect

// spotless:off
import net.fabricmc.api.ClientModInitializer
//? if >=1.21.11 {
import net.minecraft.client.gui.components.debug.DebugScreenEntries
import net.minecraft.resources.Identifier
import terrasect.gui.RegionDebugEntry
//?}
// spotless:on

object TerrasectFabricClient : ClientModInitializer {
  // spotless:off
  //? if >=1.21.11 {
  val REGION_DEBUG: Identifier =
    DebugScreenEntries.register(
      Identifier.fromNamespaceAndPath("terrasect", "region"),
      RegionDebugEntry(),
    )
  //?}
  // spotless:on

  override fun onInitializeClient() {}
}
