package terrasect.strategies

import terrasect.definition.HexSettings
import terrasect.definition.Strategy
import terrasect.definition.StrategyId
import terrasect.generation.Context
import terrasect.generation.TraversalStep
import kotlin.math.*

object HexStrategy {
  val discriminator = StrategyId.HEX.value

  data class GetCellResult(
      var q: Long = 0,
      var r: Long = 0,
      var distance: Double = 0.0,
      var isGap: Boolean = false,
  )

  val cellRef: ThreadLocal<GetCellResult> = ThreadLocal.withInitial { GetCellResult() }

  private val SQRT3 = sqrt(3.0)
  private val SIN60 = sin(Math.toRadians(60.0))
  private val COS60 = cos(Math.toRadians(60.0))
  private val TAN30 = tan(Math.toRadians(30.0))
  private const val ONE_THIRD = 1.0 / 3.0
  private const val TWO_THIRDS = 2.0 / 3.0

  fun getCell(x: Long, z: Long, radius: Int, gap: Int = 0): GetCellResult {
    val spacing = (radius + gap.coerceAtLeast(0))

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
    val isGap = distance > 0f && gap > 0
    if (isGap) {
      distance = -distance
    }

    val cell = cellRef.get()
    cell.q = q
    cell.r = r
    cell.distance = distance
    cell.isGap = isGap

    return cell
  }

  fun hexDistance(px: Double, pz: Double, radius: Int): Double {
    val x = abs(px)
    val z = abs(pz)

    val d = x * 0.5 + z * SIN60

    return max(d, x) - radius
  }

  fun traverse(
      context: Context,
      step: TraversalStep,
      settings: HexSettings,
  ): TraversalStep {
    val cell = getCell(step.x, step.z, context.region.budget, settings.ringRegion?.budget ?: 0)

    step.id.put(discriminator)
    step.id.putLong(cell.q)
    step.id.putLong(cell.r)
    step.id.putChar(Strategy.SEPARATOR)

    step.region =
        if (cell.isGap && settings.ringRegion != null) settings.ringRegion else settings.children

    return step
  }
}
