package terrasect.presets

import terrasect.definition.RegionRegistry
import terrasect.definition.Strategy

var CLIMATE_DEBUG =
  RegionRegistry().let {
    it.setRoot("minecraft:overworld", "hex")
    it.region("hex").radius(150).strategy(Strategy.hex().tiling(true))
    it.region("cell").parent("hex").strategy(Strategy.voronoi())

    it.region("voronoi1").radius(30).parent("cell").climate { temperature(-10000).humidity(5000) }
    it.region("voronoi2").radius(45).parent("cell").climate { temperature(0).humidity(0) }
    it.region("voronoi3").radius(75).parent("cell").climate { temperature(10000).humidity(-5000) }

    it.region("border").radius(500)
    return@let it
  }
