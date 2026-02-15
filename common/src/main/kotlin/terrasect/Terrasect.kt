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
    registry.region("hex").area(150.0).strategy(Strategy.hex().tiling())
    registry.region("cell").parent("hex").strategy(Strategy.voronoi()).climate {
      temperature(10000).humidity(5000)
    }

    registry.region("voronoi1").area(0.2 * 150.0).parent("cell")
    registry.region("voronoi2").area(0.3 * 150.0).parent("cell")

    registry
        .region("voronoi3")
        .area(0.5 * 150.0)
        .parent("cell")
        .strategy(Strategy.surround("surround"))

    registry.region("surround").area(50.0)
    registry.region("center").parent("voronoi3").area(100.0)

    LOGGER.info("Initializing ${Constants.MOD_NAME} common...")
  }

  fun initClient() {
    LOGGER.info("Initializing ${Constants.MOD_NAME} client...")
  }
}
