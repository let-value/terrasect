package terrasect

import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object TerrasectFabric : ModInitializer {
  private val log = LoggerFactory.getLogger(Constants.MOD_ID)

  override fun onInitialize() {
    log.info("Hello from ${Constants.MOD_NAME} on Fabric!")

    Terrasect.init(FabricLoader.getInstance().configDir)
  }
}
