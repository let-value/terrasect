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

@Mod(Constants.MOD_ID)
class TerrasectNeoForge(modEventBus: IEventBus, modContainer: ModContainer) {

  companion object {
    private val LOGGER = LogUtils.getLogger()
  }

  init {
    modEventBus.addListener(::commonSetup)
    modEventBus.addListener(::clientSetup)

    NeoForge.EVENT_BUS.register(this)

    LOGGER.info("Hello from ${Constants.MOD_NAME} on NeoForge!")
  }

  private fun commonSetup(event: FMLCommonSetupEvent) {
    LOGGER.info("${Constants.MOD_NAME} common setup")

    Terrasect.init()
  }

  private fun clientSetup(event: FMLClientSetupEvent) {
    LOGGER.info("${Constants.MOD_NAME} client setup")
    LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().user.name)
  }

  @SubscribeEvent
  fun onServerStarting(event: ServerStartingEvent) {
    LOGGER.info("${Constants.MOD_NAME} server starting")
  }
}
