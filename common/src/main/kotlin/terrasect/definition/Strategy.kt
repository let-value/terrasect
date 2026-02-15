package terrasect.definition

import terrasect.generation.TraversalStep
import terrasect.strategies.HexStrategy
import terrasect.strategies.SubdivisionStrategy
import terrasect.strategies.SurroundStrategy
import terrasect.strategies.VoronoiStrategy

enum class StrategyId(val value: Byte) {
  HEX(1),
  VORONOI(2),
  SUBDIVISION(3),
  TEMPLATE(4),
  SURROUND(5),
}

interface StrategySettings {
  fun build(definition: RegionDefinition, children: Set<Region>): Strategy
}

interface Strategy {
  fun traverse(step: TraversalStep): TraversalStep

  companion object {
    const val SEPARATOR: Char = ','

    fun hex(ringRegionName: String? = null) = HexStrategy.builder(ringRegionName)

    fun voronoi() = VoronoiStrategy.builder()

    fun subdivision() = SubdivisionStrategy.builder()

    fun surround(surroundRegionName: String) = SurroundStrategy.builder(surroundRegionName)
  }
}
