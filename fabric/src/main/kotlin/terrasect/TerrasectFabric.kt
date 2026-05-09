package terrasect

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object TerrasectFabric : ModInitializer {
  private val logger = LoggerFactory.getLogger(Constants.MOD_ID)

  override fun onInitialize() {
    logger.info("Hello from ${Constants.MOD_NAME} on Fabric!")

    Terrasect.init()
  }
}
