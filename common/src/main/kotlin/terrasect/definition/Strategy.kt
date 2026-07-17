package terrasect.definition

import java.nio.ByteBuffer
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
  val targets: List<Region>

  // True when the strategy's id fragment carries spatial instance identity beyond child selection
  // (hex q/r). Tiled strategies identify their own region through a real fragment; the rest write
  // a reserved self fragment so a region's id never collides with one of its children.
  val tiled: Boolean
    get() = false

  fun traverse(step: TraversalStep): TraversalStep

  fun locate(step: LocateStep): LocateStep?

  fun resolve(step: LocateStep, child: Region): LocateStep?

  fun writeSelf(step: LocateStep, buffer: ByteBuffer): Unit =
    error("tiled strategies have no self fragment")

  companion object {
    const val SELF_INDEX: Int = -1

    fun hex(ringRegionName: String? = null) = HexStrategy.builder(ringRegionName)

    fun voronoi() = VoronoiStrategy.builder()

    fun subdivision() = SubdivisionStrategy.builder()

    fun surround(surroundRegionName: String) = SurroundStrategy.builder(surroundRegionName)
  }
}
