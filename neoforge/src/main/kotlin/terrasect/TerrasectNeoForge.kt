package terrasect

import com.mojang.logging.LogUtils
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartingEvent

@Mod(Constants.MOD_ID)
class TerrasectNeoForge(modEventBus: IEventBus, modContainer: ModContainer) {

  companion object {
    private val log = LogUtils.getLogger()
  }

  init {
    modEventBus.addListener(::commonSetup)
    modEventBus.addListener(::clientSetup)

    NeoForge.EVENT_BUS.register(this)

    log.info("Hello from ${Constants.MOD_NAME} on NeoForge!")
  }

  private fun commonSetup(event: FMLCommonSetupEvent) {
    log.info("${Constants.MOD_NAME} common setup")

    Terrasect.init(FMLPaths.CONFIGDIR.get())
  }

  private fun clientSetup(event: FMLClientSetupEvent) {
    log.info("${Constants.MOD_NAME} client setup")
    log.info("MINECRAFT NAME >> {}", Minecraft.getInstance().user.name)
  }

  @SubscribeEvent
  fun onServerStarting(event: ServerStartingEvent) {
    log.info("${Constants.MOD_NAME} server starting")
  }
}
