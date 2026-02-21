package terrasect

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import terrasect.cache.Cache
import terrasect.definition.RegionRegistry
import terrasect.definition.Strategy

object Terrasect {
  val registry = RegionRegistry()
  val cache = Cache(100)

  private val LOGGER: Logger = LoggerFactory.getLogger(Constants.MOD_ID)

  fun init() {
    registry.setRoot("minecraft:overworld", "hex")
    registry.region("hex").area(150).strategy(Strategy.hex().tiling(true))
    registry.region("cell").parent("hex").strategy(Strategy.voronoi())

    registry.region("voronoi1").area(30).parent("cell").climate {
      temperature(-10000).humidity(5000)
    }
    registry.region("voronoi2").area(45).parent("cell").climate { temperature(0).humidity(0) }
    registry.region("voronoi3").area(75).parent("cell").climate {
      temperature(10000).humidity(-5000)
    }

    registry.region("border").area(500)

    LOGGER.info("Initializing ${Constants.MOD_NAME} common...")
  }
}
