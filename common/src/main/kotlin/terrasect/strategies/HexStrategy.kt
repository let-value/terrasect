package terrasect.strategies

import terrasect.definition.Strategy
import terrasect.definition.StrategyId
import terrasect.generation.Context
import terrasect.generation.TraversalStep
import java.lang.Math.clamp
import kotlin.math.*

object HexStrategy {
  val discriminator = StrategyId.HEX

  private val sqrt3 = sqrt(3.0f)
  private val tan30 = sqrt3 / 3.0f
  private val sin60 = sqrt3 / 2.0f
  private const val oneThird = 1.0f / 3.0f
  private const val twoThirds = 2.0f / 3.0f

  data class GetCellResult(
      var q: Int = 0,
      var r: Int = 0,
      var distance: Float = 0.0f,
      var isGap: Boolean = false,
  )

  val cellLocal: ThreadLocal<GetCellResult> = ThreadLocal.withInitial { GetCellResult() }
  val cell: GetCellResult
    get() = cellLocal.get()

  fun getCell(x: Int, z: Int, size: Int, gap: Int = 0): GetCellResult {
    val spacing = (size + gap.coerceAtLeast(0))

    val qFrac = (tan30 * x - oneThird * z) / spacing
    val rFrac = (twoThirds * z) / spacing
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

    val centerX = (sqrt3 * q + sin60 * r) * spacing
    val centerZ = (1.5f * r) * spacing

    val localX = x - centerX
    val localZ = z - centerZ

    val apothem = size * sin60
    val distance = hexSignedDistance(localX, localZ, apothem)

    cell.q = q
    cell.r = r
    cell.distance = distance
    cell.isGap = distance > 0f

    return cell
  }

  fun hexSignedDistance(px: Float, pz: Float, r: Float): Float {
    val kx = -sin60
    val ky = 0.5f
    val kz = tan30

    var x = abs(pz)
    var y = abs(px)

    val dotK = kx * x + ky * y
    val minVal = min(dotK, 0.0f)

    x -= 2.0f * minVal * kx
    y -= 2.0f * minVal * ky

    val clampLimit = kz * r
    val clampedX = x - clamp(x, -clampLimit, clampLimit)

    val lengthVecX = clampedX
    val lengthVecY = y - r

    return hypot(lengthVecX, lengthVecY) * sign(lengthVecY)
  }

  fun traverse(
      context: Context,
      step: TraversalStep,
      settings: Strategy.Hex,
  ): TraversalStep {

    getCell(step.x, step.z, context.region.budget)

    step.id.put(discriminator.value)
    step.id.putInt(cell.q)
    step.id.putInt(cell.r)
    step.id.putChar(Strategy.SEPARATOR)

    return step
  }
}
