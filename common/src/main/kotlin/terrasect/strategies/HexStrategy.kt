package terrasect.strategies

import terrasect.definition.Region
import terrasect.definition.RegionBuilder
import terrasect.definition.Strategy
import terrasect.definition.StrategySettings
import terrasect.generation.TraversalStep
import terrasect.sdf.*
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class HexCellResult(
    var q: Int = 0,
    var r: Int = 0,
    var isGap: Boolean = false,
    var centerX: Int = 0,
    var centerZ: Int = 0,
)

class HexStrategy(
    val tiling: Boolean,
    val children: Region,
    val ringRegion: Region? = null,
) : Strategy {
  val id = Strategy.SEQUENCE++
  val cellSdfRef: ThreadLocal<HexCellSdf> = ThreadLocal.withInitial { HexCellSdf() }
  val gapSdfRef: ThreadLocal<HexGapSdf> = ThreadLocal.withInitial { HexGapSdf() }

  fun getCachedCell(step: TraversalStep, apothem: Float, gap: Float): HexCellResult {
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
    val gap = if (ringRegion != null) areaToApothem(ringRegion.budget) else 0f

    val cell = getCachedCell(step, apothem, gap)

    writeId(step.id, cell)

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
    step.region = if (cell.isGap && ringRegion != null) ringRegion else children

    return step
  }

  fun writeId(buffer: ByteBuffer, cell: HexCellResult) {
    buffer.put(id)
    buffer.putInt(cell.q)
    buffer.putInt(cell.r)
    buffer.put(if (cell.isGap) 1.toByte() else 0.toByte())
  }

  fun readId(buffer: ByteBuffer): HexCellResult? {
    try {
      val strategyId = buffer.get()
      if (strategyId != id) {
        return null
      }

      val q = buffer.getInt()
      val r = buffer.getInt()
      val isGap = buffer.get() == 1.toByte()

      return HexCellResult(q, r, isGap)
    } catch (_: Exception) {
      return null
    }
  }

  companion object {

    fun getCell(x: Int, z: Int, apothem: Float, gap: Float = 0f): HexCellResult {
      val spacing = apothem + gap.coerceAtLeast(0f)

      val qFrac = (TAN30 * x - ONE_THIRD * z) / spacing
      val rFrac = (TWO_THIRDS * z) / spacing
      val sFrac = -qFrac - rFrac

      var q = qFrac.roundToInt()
      var r = rFrac.roundToInt()
      val s = sFrac.roundToInt()

      val qDiff = abs(q - qFrac)
      val rDiff = abs(r - rFrac)
      val sDiff = abs(s - sFrac)

      if (qDiff > rDiff && qDiff > sDiff) {
        q = -r - s
      } else if (rDiff > sDiff) {
        r = -q - s
      }

      val centerX = ((SQRT3 * q + SIN60 * r) * spacing).toInt()
      val centerZ = ((1.5 * r) * spacing).toInt()

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
