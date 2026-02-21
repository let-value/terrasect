package terrasect

import com.mojang.logging.LogUtils
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartingEvent

/**
 * Main mod class for NeoForge. The value here should match an entry in the
 * META-INF/neoforge.mods.toml file.
 */
@Mod(Constants.MOD_ID)
class TerrasectNeoForge(modEventBus: IEventBus, modContainer: ModContainer) {

  companion object {
    private val LOGGER = LogUtils.getLogger()
  }

  init {
    // Register the commonSetup method for mod loading
    modEventBus.addListener(::commonSetup)
    modEventBus.addListener(::clientSetup)

    // Register ourselves for server and other game events we are interested in
    NeoForge.EVENT_BUS.register(this)

    LOGGER.info("Hello from ${Constants.MOD_NAME} on NeoForge!")
  }

  private fun commonSetup(event: FMLCommonSetupEvent) {
    // Some common setup code
    LOGGER.info("${Constants.MOD_NAME} common setup")

    // Initialize common code
    Terrasect.init()
  }

  private fun clientSetup(event: FMLClientSetupEvent) {
    LOGGER.info("${Constants.MOD_NAME} client setup")
    LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().user.name)
  }

  @SubscribeEvent
  fun onServerStarting(event: ServerStartingEvent) {
    // Do something when the server starts
    LOGGER.info("${Constants.MOD_NAME} server starting")
  }
}
