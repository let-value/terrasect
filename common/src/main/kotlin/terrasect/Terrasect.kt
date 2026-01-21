package terrasect

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Terrasect {
  private val LOGGER: Logger = LoggerFactory.getLogger(Constants.MOD_ID)

  fun init() {
    LOGGER.info("Initializing ${Constants.MOD_NAME} common...")
  }

  fun initClient() {
    LOGGER.info("Initializing ${Constants.MOD_NAME} client...")
  }
}
