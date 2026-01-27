package terrasect.generation

import terrasect.definition.Region
import terrasect.sdf.Sdf2
import terrasect.strategies.HexSettings
import terrasect.strategies.HexStrategy
import java.nio.ByteBuffer
import kotlin.math.max

private object EmptySdf : Sdf2 {
  override fun invoke(x: Double, z: Double): Double = Double.NEGATIVE_INFINITY
}

private class SdfCompose : Sdf2 {
  var left: Sdf2 = EmptySdf
  var right: Sdf2 = EmptySdf

  override fun invoke(x: Double, z: Double): Double = max(left(x, z), right(x, z))
}

class TraversalStep(val context: Context) {
  val id: ByteBuffer = ByteBuffer.allocate(256)
  var region: Region = context.region
  var x: Long = 0
  var z: Long = 0
  var distance: Double = Double.NEGATIVE_INFINITY
  var sdf: Sdf2 = EmptySdf

  private var composePool = Array(8) { SdfCompose() }
  private var composeCount = 0

  fun composeSdf(bound: Sdf2) {
    sdf =
        if (sdf === EmptySdf) {
          bound
        } else {
          if (composeCount == composePool.size) {
            composePool =
                Array(composePool.size * 2) { index ->
                  if (index < composePool.size) composePool[index] else SdfCompose()
                }
          }
          val node = composePool[composeCount++]
          node.left = sdf
          node.right = bound
          node
        }
  }

  fun reset(x: Long, z: Long) {
    this.id.clear()
    this.x = x
    this.z = z
    this.region = context.region
    this.distance = Double.NEGATIVE_INFINITY
    this.sdf = EmptySdf
    this.composeCount = 0
  }
}

val Context.iterator: ThreadLocal<TraversalStep>
  get() = ThreadLocal.withInitial { TraversalStep(this) }

fun Context.step(step: TraversalStep): TraversalStep? {
  val region = step.region
  if (!region.hasChildren) {
    return null
  }

  return when (region.strategy) {
    is HexSettings -> HexStrategy.traverse(this, step, region.strategy)
    else -> throw IllegalArgumentException("Unknown generation strategy: ${region.strategy}")
  }
}

fun Context.iterate(x: Long, z: Long): TraversalStep {
  val step = this.iterator.get()
  step.reset(x, z)

  return step
}

fun Context.traverse(x: Long, z: Long): TraversalStep {
  var step = this.iterate(x, z)

  while (step.region.hasChildren) {
    step = this.step(step) ?: break
  }

  return step
}
