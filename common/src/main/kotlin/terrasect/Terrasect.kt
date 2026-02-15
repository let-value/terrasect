package terrasect

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import terrasect.cache.Cache
import terrasect.definition.RegionRegistry
import terrasect.definition.Strategy

object Terrasect {
  val registry = RegionRegistry()
  val cache = Cache()

  private val LOGGER: Logger = LoggerFactory.getLogger(Constants.MOD_ID)

  fun init() {
    registry.setRoot("minecraft:overworld", "hex")
    registry.region("hex").area(1000).strategy(Strategy.hex("border").tiling(false))
    registry.region("cell").parent("hex").strategy(Strategy.voronoi()).climate {
      temperature(10000).humidity(5000)
    }

    registry.region("voronoi1").area(30).parent("cell")
    registry.region("voronoi2").area(45).parent("cell")
    registry.region("voronoi3").area(75).parent("cell")

    registry.region("border").area(500)

    LOGGER.info("Initializing ${Constants.MOD_NAME} common...")
  }

  fun initClient() {
    LOGGER.info("Initializing ${Constants.MOD_NAME} client...")
  }
}
