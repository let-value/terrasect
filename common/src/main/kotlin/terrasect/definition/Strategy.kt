package terrasect.definition

import terrasect.generation.LocateStep
import terrasect.generation.TraversalStep
import terrasect.strategies.HexStrategy
import terrasect.strategies.SubdivisionStrategy
import terrasect.strategies.SurroundStrategy
import terrasect.strategies.VoronoiStrategy

interface StrategySettings {
  fun build(builder: RegionBuilder, children: Set<Region>): Strategy
}

interface Strategy {
  val id: Byte

  fun traverse(step: TraversalStep): TraversalStep

  fun locate(step: LocateStep): LocateStep?

  companion object {

    fun hex(ringRegionName: String? = null) = HexStrategy.builder(ringRegionName)

    fun voronoi() = VoronoiStrategy.builder()

    fun subdivision() = SubdivisionStrategy.builder()

    fun surround(surroundRegionName: String) = SurroundStrategy.builder(surroundRegionName)
  }
}
