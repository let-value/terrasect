package terrasect

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import terrasect.cache.RegionsCache

object Terrasect {
  val cache = RegionsCache(400)

  private val LOGGER: Logger = LoggerFactory.getLogger(Constants.MOD_ID)

  fun init() {

    LOGGER.info("Initializing ${Constants.MOD_NAME} common...")
  }
}
