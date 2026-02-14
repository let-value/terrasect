package terrasect.generation

import terrasect.definition.Region
import terrasect.sdf.Sdf2
import terrasect.sdf.SdfCompose
import terrasect.strategies.HexStrategy
import java.nio.ByteBuffer

class TraversalStep(val context: Context) {
  val id: ByteBuffer = ByteBuffer.allocate(256)
  var region: Region = context.region
  var x: Long = 0
  var z: Long = 0
  var distance: Double = Double.NEGATIVE_INFINITY
  val sdf = SdfCompose()

  private var composeCount = 0

  fun composeSdf(bound: Sdf2) {
    sdf.append(bound)
  }

  fun reset(x: Long, z: Long) {
    this.id.clear()
    this.x = x
    this.z = z
    this.region = context.region
    this.distance = Double.NEGATIVE_INFINITY
    this.sdf.reset()
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
    is HexStrategy -> HexStrategy.traverse(this, step, region.strategy)
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
