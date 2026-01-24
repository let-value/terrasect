package terrasect.strategies

import terrasect.definition.GenerationStrategy
import terrasect.generation.Context
import terrasect.generation.TraversalStep
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

object HexStrategy {
  fun getCell(x: Int, z: Int, size: Int): Pair<Int, Int> {
    val q = (sqrt(3.0) / 3 * x - 1.0 / 3 * z) / size
    val r = (2.0 / 3 * z) / size

    val rq = q.roundToInt()
    val rr = r.roundToInt()
    val rs = (-q - r).roundToInt()

    val qDiff = abs(rq - q)
    val rDiff = abs(rr - r)
    val sDiff = abs(rs + q + r)

    var finalQ = rq
    var finalR = rr

    if (qDiff > rDiff && qDiff > sDiff) {
      finalQ = -rr - rs
    } else if (rDiff > sDiff) {
      finalR = -rq - rs
    }

    return Pair(finalQ, finalR)
  }

  fun traverse(
      context: Context,
      step: TraversalStep,
      settings: GenerationStrategy.Hex,
  ): TraversalStep {
    return step
  }
}
