package terrasect.strategies

import terrasect.definition.Strategy
import terrasect.definition.StrategyId
import terrasect.generation.Context
import terrasect.generation.TraversalStep
import kotlin.math.*

object HexStrategy {
  val discriminator = StrategyId.HEX

  data class GetCellResult(
      var q: Int = 0,
      var r: Int = 0,
      var distance: Float = 0.0f,
      var isGap: Boolean = false,
  )

  val cellLocal: ThreadLocal<GetCellResult> = ThreadLocal.withInitial { GetCellResult() }
  val cell: GetCellResult
    get() = cellLocal.get()

  private val SQRT3 = sqrt(3.0f)
  private val SIN60 = sin(Math.toRadians(60.0)).toFloat()
  private val COS60 = cos(Math.toRadians(60.0)).toFloat()
  private val TAN30 = tan(Math.toRadians(30.0)).toFloat()
  private const val ONE_THIRD = 1.0f / 3.0f
  private const val TWO_THIRDS = 2.0f / 3.0f

  fun getCell(x: Int, z: Int, size: Int, gap: Int = 0): GetCellResult {
    val spacing = (size + gap.coerceAtLeast(0))

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

    val centerX = (SQRT3 * q + SIN60 * r) * spacing
    val centerZ = (1.5f * r) * spacing

    val localX = x - centerX
    val localZ = z - centerZ

    var distance = hexDistance(localX, localZ, size)
    val isGap = distance > 0f && gap > 0
    if (isGap) {
      distance = -distance
    }

    cell.q = q
    cell.r = r
    cell.distance = distance
    cell.isGap = isGap

    return cell
  }

  fun hexDistance(px: Float, pz: Float, r: Int): Float {
    val x = abs(px)
    val z = abs(pz)

    val dist = max(x, x * COS60 + z * SIN60)

    val apothem = r * SIN60

    return dist - apothem
  }

  fun traverse(
      context: Context,
      step: TraversalStep,
      settings: Strategy.Hex,
  ): TraversalStep {

    val cell = getCell(step.x, step.z, context.region.budget)

    step.id.put(discriminator.value)
    step.id.putInt(cell.q)
    step.id.putInt(cell.r)
    step.id.putChar(Strategy.SEPARATOR)

    return step
  }
}
