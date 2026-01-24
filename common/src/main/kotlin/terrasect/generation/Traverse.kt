package terrasect.generation

import terrasect.definition.GenerationStrategy
import terrasect.definition.Region
import terrasect.strategies.HexStrategy
import java.nio.ByteBuffer

class TraversalStep(val context: Context) {
  val id = ByteBuffer.allocate(256)
  var region: Region = context.region
  var x: Int = 0
  var z: Int = 0
  var edgeDistance: Float = 0f

  fun reset(x: Int, z: Int) {
    this.id.clear()
    this.x = x
    this.z = z
    this.region = context.region
    this.edgeDistance = 0f
  }
}

val Context.iterator: ThreadLocal<TraversalStep>
  get() = ThreadLocal.withInitial { TraversalStep(this) }

fun Context.step(step: TraversalStep): TraversalStep? {
  val region = step.region
  if (!region.hasChildren) {
    return null
  }

  return when (region.generationStrategy) {
    is GenerationStrategy.Hex -> HexStrategy.traverse(this, step, region.generationStrategy)
    else ->
        throw IllegalArgumentException("Unknown generation strategy: ${region.generationStrategy}")
  }
}

fun Context.iterate(x: Int, z: Int): TraversalStep {
  val step = this.iterator.get()
  step.reset(x, z)

  return step
}

fun Context.traverse(x: Int, z: Int): TraversalStep {
  var iteration = this.iterate(x, z)

  while (iteration.region.hasChildren) {
    iteration = this.step(iteration) ?: break
  }

  return iteration
}
