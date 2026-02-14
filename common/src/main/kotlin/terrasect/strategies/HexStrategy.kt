package terrasect.strategies

import terrasect.definition.*
import terrasect.generation.TraversalStep
import terrasect.sdf.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

private val discriminator = StrategyId.HEX.value

data class HexCellResult(
    var q: Long = 0,
    var r: Long = 0,
    var distance: Double = 0.0,
    var isGap: Boolean = false,
    var centerX: Double = 0.0,
    var centerZ: Double = 0.0,
)

class HexStrategy(val children: Region, val ringRegion: Region? = null) : Strategy {
  val cellSdfRef: ThreadLocal<HexCellSdf> = ThreadLocal.withInitial { HexCellSdf() }
  val gapSdfRef: ThreadLocal<HexGapSdf> = ThreadLocal.withInitial { HexGapSdf() }

  companion object {
    fun builder(ringRegionName: String? = null) = Builder(ringRegionName)

    val cellRef: ThreadLocal<HexCellResult> = ThreadLocal.withInitial { HexCellResult() }

    fun getCell(x: Long, z: Long, apothem: Double, gap: Double = 0.0): HexCellResult {
      val spacing = apothem + gap.coerceAtLeast(0.0)

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

      var distance = hexDistance(localX, localZ, apothem)
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

      return cell
    }

    fun traverse(step: TraversalStep, settings: HexStrategy): TraversalStep {
      val apothem = areaToApothem(step.region.budget)
      val gap = settings.ringRegion?.let { areaToApothem(it.budget) } ?: 0.0
      val cell = getCell(step.x, step.z, apothem, gap)

      step.id.put(discriminator)
      step.id.putLong(cell.q)
      step.id.putLong(cell.r)

      if (cell.isGap) {
        val sdf = settings.gapSdfRef.get()
        sdf.centerX = cell.centerX
        sdf.centerZ = cell.centerZ
        sdf.apothem = apothem
        sdf.gap = gap
        step.sdf.append(sdf)
      } else {
        val sdf = settings.cellSdfRef.get()
        sdf.centerX = cell.centerX
        sdf.centerZ = cell.centerZ
        sdf.apothem = apothem
        step.sdf.append(sdf)
      }

      step.distance = max(step.distance, cell.distance)

      step.region =
          (if (cell.isGap && settings.ringRegion != null) settings.ringRegion
          else settings.children)

      return step
    }
  }

  class Builder(var ringRegionName: String? = null) : StrategySettings {

    fun ringRegionName(ringRegionName: String?) = apply { this.ringRegionName = ringRegionName }

    override fun build(definition: RegionDefinition, children: Set<Region>): HexStrategy {
      val region = children.firstOrNull() ?: Region.empty(definition.name + "_placeholder")
      return HexStrategy(
          region,
          ringRegionName?.let { definition.registry.build(it) },
      )
    }
  }
}
