package terrasect.strategies

import terrasect.definition.*
import terrasect.generation.Context
import terrasect.generation.TraversalStep
import terrasect.sdf.HexCellSdf
import terrasect.sdf.HexGapSdf
import terrasect.sdf.hexDistance
import kotlin.math.*

private val discriminator = StrategyId.HEX.value
private val SQRT3 = sqrt(3.0)
private val SIN60 = sin(Math.toRadians(60.0))
private val TAN30 = tan(Math.toRadians(30.0))
private const val ONE_THIRD = 1.0 / 3.0
private const val TWO_THIRDS = 2.0 / 3.0

data class GetCellResult(
    var q: Long = 0,
    var r: Long = 0,
    var distance: Double = 0.0,
    var isGap: Boolean = false,
    var centerX: Double = 0.0,
    var centerZ: Double = 0.0,
    var radius: Int = 0,
)

class HexStrategy(val children: Region, val ringRegion: Region? = null) : Strategy {
  val cellSdfRef: ThreadLocal<HexCellSdf> = ThreadLocal.withInitial { HexCellSdf() }
  val gapSdfRef: ThreadLocal<HexGapSdf> = ThreadLocal.withInitial { HexGapSdf() }

  companion object {
    fun builder(ringRegionName: String? = null) = Builder(ringRegionName)

    val cellRef: ThreadLocal<GetCellResult> = ThreadLocal.withInitial { GetCellResult() }

    fun getCell(x: Long, z: Long, radius: Int, gap: Int = 0): GetCellResult {
      val spacing = radius + gap.coerceAtLeast(0)

      val qFrac = (TAN30 * x - ONE_THIRD * z) / spacing
      val rFrac = (TWO_THIRDS * z) / spacing
      val sFrac = -qFrac - rFrac

      var q = qFrac.roundToLong()
      var r = rFrac.roundToLong()
      val s = sFrac.roundToLong()

      val qDiff = abs(q - qFrac)
      val rDiff = abs(r - rFrac)
      val sDiff = abs(s - sFrac)

      if (qDiff > rDiff && qDiff > sDiff) {
        q = -r - s
      } else if (rDiff > sDiff) {
        r = -q - s
      }

      val centerX = (SQRT3 * q + SIN60 * r) * spacing
      val centerZ = (1.5 * r) * spacing

      val localX = x - centerX
      val localZ = z - centerZ

      var distance = hexDistance(localX, localZ, radius)
      val isGap = gap > 0 && distance > 0.0
      if (isGap) {
        val outerDistance = hexDistance(localX, localZ, spacing)
        distance = max(outerDistance, -distance)
      }

      val cell = cellRef.get()
      cell.q = q
      cell.r = r
      cell.distance = distance
      cell.isGap = isGap
      cell.centerX = centerX
      cell.centerZ = centerZ
      cell.radius = radius

      return cell
    }

    fun traverse(
        context: Context,
        step: TraversalStep,
        settings: HexStrategy,
    ): TraversalStep {
      val radius = context.region.budget
      val gap = settings.ringRegion?.budget ?: 0
      val cell = getCell(step.x, step.z, radius, gap)

      step.id.put(discriminator)
      step.id.putLong(cell.q)
      step.id.putLong(cell.r)
      step.id.putChar(Strategy.SEPARATOR)

      if (cell.isGap) {
        val sdf = settings.gapSdfRef.get()
        sdf.centerX = cell.centerX
        sdf.centerZ = cell.centerZ
        sdf.radius = cell.radius
        sdf.gap = gap
        step.composeSdf(sdf)
      } else {
        val sdf = settings.cellSdfRef.get()
        sdf.centerX = cell.centerX
        sdf.centerZ = cell.centerZ
        sdf.radius = cell.radius
        step.composeSdf(sdf)
      }

      step.distance = max(step.distance, cell.distance)

      step.region =
          if (cell.isGap && settings.ringRegion != null) settings.ringRegion else settings.children

      return step
    }
  }

  class Builder(var ringRegionName: String? = null) : StrategySettings {

    fun ringRegionName(ringRegionName: String?) = apply { this.ringRegionName = ringRegionName }

    override fun build(definition: RegionDefinition, children: Set<Region>) =
        HexStrategy(children.first(), ringRegionName?.let { RegionRegistry.build(it) })
  }
}
