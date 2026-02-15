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
    var isGap: Boolean = false,
    var centerX: Double = 0.0,
    var centerZ: Double = 0.0,
)

class HexStrategy(
    val tiling: Boolean,
    val children: Region,
    val ringRegion: Region? = null,
) : Strategy {
  val cellSdfRef: ThreadLocal<HexCellSdf> = ThreadLocal.withInitial { HexCellSdf() }
  val gapSdfRef: ThreadLocal<HexGapSdf> = ThreadLocal.withInitial { HexGapSdf() }

  fun getCachedCell(step: TraversalStep, apothem: Double, gap: Double): HexCellResult {
    val skipCache = tiling || step.cache == null
    if (skipCache) {
      return getCell(step.x, step.z, apothem, gap)
    }

    val cache = step.cache!!
    val key = cache.getKey(step.id)

    val cached = cache.hex.getIfPresent(key)
    if (cached != null) {
      return cached
    }

    val cell = getCell(step.x, step.z, apothem, gap)
    cache.hex.put(key, cell)

    return cell
  }

  override fun traverse(step: TraversalStep): TraversalStep {
    val apothem = areaToApothem(step.region.budget)
    val gap = if (ringRegion != null) areaToApothem(ringRegion.budget) else 0.0

    step.id.put(discriminator)
    val cell = getCachedCell(step, apothem, gap)

    step.id.putLong(cell.q)
    step.id.putLong(cell.r)

    if (cell.isGap) {
      val sdf = gapSdfRef.get()
      sdf.centerX = cell.centerX
      sdf.centerZ = cell.centerZ
      sdf.apothem = apothem
      sdf.gap = gap
      step.sdf.append(sdf)
    } else {
      val sdf = cellSdfRef.get()
      sdf.centerX = cell.centerX
      sdf.centerZ = cell.centerZ
      sdf.apothem = apothem
      step.sdf.append(sdf)
    }

    val distance = step.sdf(step.x, step.z)
    step.distance = max(step.distance, distance)

    step.region = (if (cell.isGap && ringRegion != null) ringRegion else children)

    return step
  }

  companion object {

    fun getCell(x: Double, z: Double, apothem: Double, gap: Double = 0.0): HexCellResult {
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

      val distance = hexDistance(localX, localZ, apothem)
      val isGap = gap > 0 && distance > 0.0

      val cell = HexCellResult()
      cell.q = q
      cell.r = r
      cell.isGap = isGap
      cell.centerX = centerX
      cell.centerZ = centerZ

      return cell
    }

    fun builder(ringRegionName: String? = null) = Builder(ringRegionName)
  }

  class Builder(var ringRegionName: String? = null) : StrategySettings {
    var tiling = true

    fun ringRegionName(ringRegionName: String?) = apply { this.ringRegionName = ringRegionName }

    fun tiling(value: Boolean = true) = apply { this.tiling = value }

    override fun build(builder: RegionBuilder, children: Set<Region>): HexStrategy {
      val region =
          children.find { it.name != ringRegionName } ?: Region.empty("${builder.name}_center")
      val ringRegion = ringRegionName?.let { builder.registry.buildTree(it) }
      return HexStrategy(tiling, region, ringRegion)
    }
  }
}
