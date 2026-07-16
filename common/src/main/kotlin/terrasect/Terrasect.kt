package terrasect

import java.nio.file.Path
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import terrasect.cache.RegionsCache
import terrasect.config.TerrasectConfigManager

object Terrasect {
  val cache = RegionsCache(400)

  private val log: Logger = LoggerFactory.getLogger(Constants.MOD_ID)

  fun init(configRoot: Path) {
    log.info("Initializing ${Constants.MOD_NAME} common...")
    TerrasectConfigManager.initialize(configRoot)
  }
}
