package terrasect.generation

import java.nio.ByteBuffer
import terrasect.cache.RegionsCache
import terrasect.definition.Region
import terrasect.instrumentation.TerrasectInstr
import terrasect.instrumentation.TerrasectMetricEvent
import terrasect.sdf.SdfCompose

class Traverser(val seed: Long, val root: Region) {
  val iterator: ThreadLocal<TraversalStep> = ThreadLocal.withInitial { TraversalStep(this) }

  fun iterate(x: Int, z: Int, cache: RegionsCache? = null): TraversalStep {
    val step = this.iterator.get()
    step.reset(x, z, cache)

    return step
  }

  fun traverse(x: Int, z: Int, cache: RegionsCache? = null): TraversalStep {
    var step = this.iterate(x, z, cache)

    while (step.region.hasChildren) {
      step = step.next() ?: break
    }

    TerrasectInstr.traversal.count(TerrasectMetricEvent.TRAVERSAL_COMPLETED)
    return step
  }
}

class TraversalStep(val traverser: Traverser) {
  val id: ByteBuffer = ByteBuffer.allocate(256)
  val sdf = SdfCompose()

  var cache: RegionsCache? = null
  var region: Region = traverser.root
  var x: Int = 0
  var z: Int = 0
  var distance: Float = Float.NEGATIVE_INFINITY

  fun reset(x: Int, z: Int, cache: RegionsCache? = null) {
    this.id.clear()
    this.sdf.reset()

    this.cache = cache
    this.region = traverser.root
    this.x = x
    this.z = z
    this.distance = Float.NEGATIVE_INFINITY
  }

  fun next(): TraversalStep? {
    val region = this.region

    if (!region.hasChildren) {
      return null
    }

    val next = region.strategy?.traverse(this) ?: return null
    TerrasectInstr.traversal.count(TerrasectMetricEvent.TRAVERSAL_STEP, "region") {
      next.region.name
    }
    return next
  }
}
