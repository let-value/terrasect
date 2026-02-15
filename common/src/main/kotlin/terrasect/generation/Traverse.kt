package terrasect.generation

import terrasect.ChunkAccessExtender
import terrasect.definition.Region
import terrasect.definition.Strategy
import terrasect.sdf.SdfCompose
import java.nio.ByteBuffer

open class Traverse(
    val seed: Long,
    val root: Region,
) {
  val iterator: ThreadLocal<TraversalStep>
    get() = ThreadLocal.withInitial { TraversalStep(this) }

  fun iterate(x: Double, z: Double, chunk: ChunkAccessExtender? = null): TraversalStep {
    val step = this.iterator.get()
    step.reset(x, z, chunk)

    return step
  }

  fun traverse(x: Double, z: Double, chunk: ChunkAccessExtender? = null): TraversalStep {
    var step = this.iterate(x, z, chunk)

    while (step.region.hasChildren) {
      step = step.next() ?: break
    }

    return step
  }
}

class TraversalStep(val traverse: Traverse) {
  val id: ByteBuffer = ByteBuffer.allocate(256)
  val sdf = SdfCompose()

  var chunk: ChunkAccessExtender? = null
  var region: Region = traverse.root
  var x: Double = 0.0
  var z: Double = 0.0
  var distance: Double = Double.NEGATIVE_INFINITY

  fun reset(x: Double, z: Double, chunk: ChunkAccessExtender? = null) {
    this.id.clear()
    this.sdf.reset()

    this.chunk = chunk
    this.region = traverse.root
    this.x = x
    this.z = z
    this.distance = Double.NEGATIVE_INFINITY
  }

  fun next(): TraversalStep? {
    val region = this.region

    if (!region.hasChildren) {
      return null
    }

    val step = region.strategy?.traverse(this) ?: return null

    step.id.putChar(Strategy.SEPARATOR)
    return step
  }
}
