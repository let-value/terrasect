package terrasect

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import terrasect.cache.RegionsCache
import terrasect.definition.PresetRegistry
import terrasect.definition.Strategy

object Terrasect {
  val presets = PresetRegistry()
  val cache = RegionsCache(400)

  private val LOGGER: Logger = LoggerFactory.getLogger(Constants.MOD_ID)

  fun init() {

    presets.preset("minecraft:normal").let {
      it.setRoot("minecraft:overworld", "hex")
      it.region("hex").radius(150).strategy(Strategy.hex().tiling(true))
      it.region("cell").parent("hex").strategy(Strategy.voronoi())

      it.region("voronoi1").radius(30).parent("cell").climate { temperature(-10000).humidity(5000) }
      it.region("voronoi2").radius(45).parent("cell").climate { temperature(0).humidity(0) }
      it.region("voronoi3").radius(75).parent("cell").climate { temperature(10000).humidity(-5000) }

      it.region("border").radius(500)
    }

    LOGGER.info("Initializing ${Constants.MOD_NAME} common...")
  }
}
