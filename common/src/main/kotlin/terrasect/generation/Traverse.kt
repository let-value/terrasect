package terrasect.generation

import terrasect.ChunkAccessExtender
import terrasect.definition.Region
import terrasect.definition.Strategy
import terrasect.sdf.SdfCompose
import terrasect.strategies.HexStrategy
import terrasect.strategies.SubdivisionStrategy
import terrasect.strategies.SurroundStrategy
import terrasect.strategies.VoronoiStrategy
import java.nio.ByteBuffer

open class Traverse(
    val seed: Long,
    val root: Region,
) {
  val iterator: ThreadLocal<TraversalStep>
    get() = ThreadLocal.withInitial { TraversalStep(this) }

  fun iterate(x: Long, z: Long, chunk: ChunkAccessExtender? = null): TraversalStep {
    val step = this.iterator.get()
    step.reset(x, z, chunk)

    return step
  }

  fun traverse(x: Long, z: Long, chunk: ChunkAccessExtender? = null): TraversalStep {
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
  var x: Long = 0
  var z: Long = 0
  var distance: Double = Double.NEGATIVE_INFINITY

  fun reset(x: Long, z: Long, chunk: ChunkAccessExtender? = null) {
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

    val step =
        when (region.strategy) {
          is HexStrategy -> HexStrategy.traverse(this, region.strategy)
          is VoronoiStrategy -> VoronoiStrategy.traverse(this, region.strategy)
          is SubdivisionStrategy -> SubdivisionStrategy.traverse(this, region.strategy)
          is SurroundStrategy -> SurroundStrategy.traverse(this, region.strategy)
          else -> throw IllegalArgumentException("Unknown generation strategy: ${region.strategy}")
        }

    step.id.putChar(Strategy.SEPARATOR)
    return step
  }
}
