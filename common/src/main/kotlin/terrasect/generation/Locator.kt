package terrasect.generation

import terrasect.cache.Cache
import terrasect.definition.Region
import terrasect.definition.Strategy
import terrasect.sdf.Sdf2
import terrasect.sdf.SdfCompose
import java.nio.ByteBuffer

data class LocatorResult(
    val region: Region,
    val centerX: Int,
    val centerZ: Int,
    val sdf: Sdf2,
    val distance: Float,
    val ambiguous: Boolean = false,
)

class Locator(val seed: Long, val root: Region) {
  private data class StrategyNode(val region: Region, val strategy: Strategy)

  private val strategyLookup: Map<Byte, StrategyNode> = buildLookup()
  val iterator: ThreadLocal<LocateStep> = ThreadLocal.withInitial { LocateStep(this) }

  fun iterate(id: ByteBuffer, cache: Cache? = null): LocateStep {
    val step = iterator.get()
    step.reset(normalized(id), cache)
    return step
  }

  fun locate(address: String, cache: Cache? = null): LocatorResult? {
    return locate(Address.deserialize(address), cache)
  }

  fun locate(id: ByteBuffer, cache: Cache? = null): LocatorResult? {
    var step = iterate(id, cache)
    while (step.id.hasRemaining()) {
      step = step.next() ?: return null
    }
    return step.result()
  }

  internal fun resolve(region: Region, strategyId: Byte): Strategy? {
    val node = strategyLookup[strategyId] ?: return null
    if (node.region !== region) {
      return null
    }
    return node.strategy
  }

  private fun buildLookup(): Map<Byte, StrategyNode> {
    val map = HashMap<Byte, StrategyNode>()

    fun visit(region: Region) {
      val strategy = region.strategy
      if (strategy != null) {
        map[strategy.id] = StrategyNode(region, strategy)
      }

      for (child in region.children) {
        visit(child)
      }
    }

    visit(root)
    return map
  }

  private fun normalized(id: ByteBuffer): ByteBuffer {
    val source = id.duplicate()
    val end = if (source.position() > 0) source.position() else source.limit()
    source.position(0)
    source.limit(end)
    return source
  }
}

class LocateStep(val locator: Locator) {
  val sdf = SdfCompose()

  lateinit var id: ByteBuffer
  var cache: Cache? = null
  var region: Region = locator.root
  var centerX: Int = 0
  var centerZ: Int = 0
  var ambiguous: Boolean = false

  fun reset(id: ByteBuffer, cache: Cache? = null) {
    this.id = id
    this.cache = cache
    this.sdf.reset()

    this.region = locator.root
    this.centerX = 0
    this.centerZ = 0
    this.ambiguous = false
  }

  fun next(): LocateStep? {
    val strategyId = peekStrategyId() ?: return null
    val strategy = locator.resolve(region, strategyId) ?: return null
    return strategy.locate(this)
  }

  fun peekStrategyId(): Byte? {
    if (!id.hasRemaining()) {
      return null
    }

    return id.get(id.position())
  }

  fun result(): LocatorResult {
    val baked = sdf.bake()
    return LocatorResult(
        region = region,
        centerX = centerX,
        centerZ = centerZ,
        sdf = baked,
        distance = baked(centerX, centerZ),
        ambiguous = ambiguous,
    )
  }
}
