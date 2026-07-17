package terrasect.generation

import java.nio.ByteBuffer
import terrasect.cache.RegionsCache
import terrasect.definition.Region
import terrasect.instrumentation.TerrasectInstr
import terrasect.instrumentation.TerrasectMetricEvent
import terrasect.sdf.DecoratedSdf
import terrasect.sdf.DomainDecoration
import terrasect.sdf.LayerDecoration
import terrasect.sdf.Sdf2
import terrasect.sdf.SdfCompose
import terrasect.sdf.warpPoint
import terrasect.utils.first
import terrasect.utils.second

fun prefixSeed(seed: Long, id: ByteBuffer): Long {
  var mixed = seed
  for (i in 0 until id.position()) {
    mixed = mixed * 31 + id.get(i)
  }
  return mixed
}

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
  // Canonical point of the current region instance, set by the parent strategy on descent.
  // Deterministic per instance (unlike x/z) so cached plans derived from it are stable; it may
  // fall slightly outside the instance (surround rings), which estimateBounds tolerates.
  var centerX: Int = 0
  var centerZ: Int = 0
  var distance: Float = Float.NEGATIVE_INFINITY
  // Query point in decorated space. Strategies decide ownership on qx/qz so both sides of every
  // edge see the same warped point and the partition survives decoration.
  var qx: Int = 0
  var qz: Int = 0
  var domainChain: List<DomainDecoration> = emptyList()
  var layerOps: List<LayerDecoration> = emptyList()
  private var enteredPosition = -1

  fun reset(x: Int, z: Int, cache: RegionsCache? = null) {
    this.id.clear()
    this.sdf.reset()

    this.cache = cache
    this.region = traverser.root
    this.x = x
    this.z = z
    this.centerX = 0
    this.centerZ = 0
    this.distance = Float.NEGATIVE_INFINITY
    this.qx = x
    this.qz = z
    this.domainChain = emptyList()
    this.layerOps = emptyList()
    this.enteredPosition = -1
  }

  fun append(sdf: Sdf2) {
    if (domainChain.isEmpty() && layerOps.isEmpty()) {
      this.sdf.append(sdf)
    } else {
      this.sdf.append(DecoratedSdf(sdf, domainChain, layerOps))
    }
  }

  fun enter(region: Region) {
    val position = id.position()
    if (position == enteredPosition) {
      return
    }
    enteredPosition = position
    if (region.decorations.isEmpty()) {
      layerOps = emptyList()
      return
    }

    var seed = prefixSeed(traverser.seed, id)
    val ops = ArrayList<LayerDecoration>()
    var chain = domainChain
    for (decoration in region.decorations) {
      when (val instance = decoration.instantiate(seed, centerX, centerZ)) {
        is DomainDecoration -> chain = chain + instance
        is LayerDecoration -> ops += instance
      }
      seed = seed * 31 + 17
    }
    domainChain = chain
    layerOps = ops
    val warped = warpPoint(domainChain, x, z)
    qx = warped.first()
    qz = warped.second()
  }

  fun next(): TraversalStep? {
    val region = this.region

    if (!region.hasChildren) {
      return null
    }

    enter(region)
    val next = region.strategy?.traverse(this) ?: return null
    TerrasectInstr.traversal.count(TerrasectMetricEvent.TRAVERSAL_STEP, "region") {
      next.region.name
    }
    return next
  }
}
