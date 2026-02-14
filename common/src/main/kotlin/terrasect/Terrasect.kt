package terrasect

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import terrasect.definition.RegionRegistry

object Terrasect {
  val registry = RegionRegistry()
  private val LOGGER: Logger = LoggerFactory.getLogger(Constants.MOD_ID)

  fun init() {
    LOGGER.info("Initializing ${Constants.MOD_NAME} common...")
  }

  fun initClient() {
    LOGGER.info("Initializing ${Constants.MOD_NAME} client...")
  }
}
