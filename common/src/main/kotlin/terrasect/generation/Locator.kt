package terrasect.generation

import java.nio.ByteBuffer
import terrasect.cache.RegionsCache
import terrasect.definition.Region
import terrasect.definition.Strategy
import terrasect.sdf.Sdf2
import terrasect.sdf.SdfCompose
import terrasect.sdf.translate

data class Qualifier(val name: String, val address: String)

data class LocatorResult(
  val region: Region,
  val centerX: Int,
  val centerZ: Int,
  val sdf: Sdf2,
  val distance: Float,
  val ambiguous: Boolean = false,
  val chain: List<Qualifier> = emptyList(),
)

class Locator(val seed: Long, val root: Region) {
  // Generator-space translation of the origin anchor, mirroring Traverser.offset. Results are
  // reported in world space (center and sdf shifted by -offset) so `/ts` output lines up with the
  // player's coordinates once anchoring moves world (0, 0) onto the anchor's center.
  var offsetX: Int = 0
  var offsetZ: Int = 0

  private class Node(val region: Region, val parent: Node?)

  // Each id fragment belongs to the region whose strategy wrote it (the writer); the instance of
  // a region with a strategy is therefore the id prefix through its own fragment, and only leaf
  // regions are identified by their arrival prefix. This is what makes `.hex` on a tiling root
  // resolve to a concrete tile instead of the whole plane.
  private class DecodedId(val buffer: ByteBuffer, val levels: List<Level>) {
    class Level(val writer: Region, val target: Region, val start: Int, val end: Int)

    fun fragment(
      position: Int,
      writer: Region,
      target: Region?,
      constructed: ByteBuffer,
    ): ByteArray? {
      val level =
        levels.firstOrNull {
          it.start == position && it.writer === writer && (target == null || it.target === target)
        } ?: return null
      for (i in 0 until position) {
        if (buffer.get(i) != constructed.get(i)) {
          return null
        }
      }

      val fragment = ByteArray(level.end - level.start)
      buffer.get(level.start, fragment)
      return fragment
    }
  }

  private val nodesByName: Map<String, List<Node>> = buildIndex()
  val iterator: ThreadLocal<LocateStep> = ThreadLocal.withInitial { LocateStep(this) }

  fun iterate(id: ByteBuffer, cache: RegionsCache? = null): LocateStep {
    val step = iterator.get()
    step.reset(normalized(id), cache)
    return step
  }

  fun locate(address: String, cache: RegionsCache? = null): LocatorResult? {
    return locate(Address.deserialize(address), cache)
  }

  fun locate(id: ByteBuffer, cache: RegionsCache? = null): LocatorResult? {
    val step = iterate(id, cache)
    while (step.id.hasRemaining()) {
      step.next() ?: return null
    }
    return step.result()
  }

  fun query(
    selector: String,
    context: ByteBuffer? = null,
    cache: RegionsCache? = null,
  ): LocatorResult? {
    val parts = Selector.parse(selector)
    if (parts.isEmpty()) {
      return null
    }

    val sources = mutableListOf<DecodedId>()
    val names = arrayOfNulls<String>(parts.size)
    for (i in parts.indices.reversed()) {
      val part = parts[i]
      names[i] = part.name
      val id = part.id ?: continue

      val decoded = decode(Address.deserialize(id), cache) ?: return null
      val last = decoded.levels.lastOrNull()
      val arrival = last?.target?.name ?: root.name
      if (part.name != null && part.name != arrival && part.name != last?.writer?.name) {
        return null
      }

      names[i] = part.name ?: arrival
      sources += decoded
    }
    if (context != null) {
      decode(context, cache)?.let(sources::add)
    }

    val candidates =
      nodesByName[names.last()].orEmpty().filter { matches(it, names, parts, parts.size - 1) }

    var best: LocatorResult? = null
    var bestUsed = -1
    var matched = 0
    for (candidate in candidates) {
      val (result, used) = resolve(candidate, sources, cache) ?: continue
      matched++
      if (used > bestUsed) {
        best = result
        bestUsed = used
      }
    }

    if (matched > 1) {
      best = best?.copy(ambiguous = true)
    }
    return best
  }

  internal fun resolve(region: Region, strategyId: Byte): Strategy? {
    return region.strategy?.takeIf { it.id == strategyId }
  }

  private fun matches(
    node: Node,
    names: Array<String?>,
    parts: List<SelectorPart>,
    i: Int,
  ): Boolean {
    if (node.region.name != names[i]) {
      return false
    }
    if (i == 0) {
      return true
    }

    if (parts[i].immediate) {
      val parent = node.parent ?: return false
      return matches(parent, names, parts, i - 1)
    }

    var parent = node.parent
    while (parent != null) {
      if (matches(parent, names, parts, i - 1)) {
        return true
      }
      parent = parent.parent
    }
    return false
  }

  private fun resolve(
    node: Node,
    sources: List<DecodedId>,
    cache: RegionsCache?,
  ): Pair<LocatorResult, Int>? {
    val path = generateSequence(node) { it.parent }.toList().asReversed()
    val step = LocateStep(this)
    step.reset(ByteBuffer.allocate(256), cache)

    val chain = mutableListOf<Qualifier>()
    var used = 0
    for (i in 0 until path.size - 1) {
      val parent = path[i].region
      val child = path[i + 1].region
      val start = step.id.position()
      step.enter(parent)
      when (descend(step, sources, parent, child)) {
        true -> used++
        false -> parent.strategy?.resolve(step, child) ?: return null
        null -> return null
      }
      chain += Qualifier(parent.name, selfAddress(step, parent, start))
    }

    val region = node.region
    val strategy = region.strategy
    val ownTarget = strategy?.targets?.firstOrNull()
    if (strategy != null && ownTarget != null) {
      step.enter(region)
      if (strategy.tiled) {
        when (descend(step, sources, region, null)) {
          true -> used++
          false -> strategy.resolve(step, ownTarget) ?: return null
          null -> return null
        }
      } else {
        val position = step.id.position()
        strategy.writeSelf(step, step.id)
        step.id.position(position)
        step.next() ?: return null
      }
    }
    chain += Qualifier(region.name, Address.serialize(step.id))

    return step.result(region, chain) to used
  }

  // The display address of a region with a child-selecting strategy replaces the descent fragment
  // with the reserved self fragment, so the region's id never aliases the child that happened to
  // be descended into. Tiled strategies (hex) keep the real fragment — it is the tile identity.
  private fun selfAddress(step: LocateStep, parent: Region, start: Int): String {
    val strategy = parent.strategy ?: return Address.serialize(step.id)
    if (strategy.tiled) {
      return Address.serialize(step.id)
    }

    val scratch = ByteBuffer.allocate(start + 16)
    step.id.get(0, scratch.array(), 0, start)
    scratch.position(start)
    strategy.writeSelf(step, scratch)
    return Address.serialize(scratch)
  }

  private fun descend(
    step: LocateStep,
    sources: List<DecodedId>,
    writer: Region,
    target: Region?,
  ): Boolean? {
    val position = step.id.position()
    val fragment =
      sources.firstNotNullOfOrNull { it.fragment(position, writer, target, step.id) }
        ?: return false
    step.id.put(fragment)
    step.id.position(position)
    return if (step.next() != null) true else null
  }

  private fun decode(id: ByteBuffer, cache: RegionsCache?): DecodedId? {
    val source = normalized(id)
    val step = LocateStep(this)
    step.reset(source, cache)

    val levels = mutableListOf<DecodedId.Level>()
    while (step.id.hasRemaining()) {
      val start = step.id.position()
      val writer = step.region
      step.next() ?: return null
      levels += DecodedId.Level(writer, step.region, start, step.id.position())
    }
    return DecodedId(source, levels)
  }

  private fun buildIndex(): Map<String, List<Node>> {
    val map = HashMap<String, MutableList<Node>>()

    fun visit(region: Region, parent: Node?) {
      val node = Node(region, parent)
      map.getOrPut(region.name) { mutableListOf() }.add(node)
      for (child in region.strategy?.targets.orEmpty()) {
        visit(child, node)
      }
    }

    visit(root, null)
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
  var cache: RegionsCache? = null
  var region: Region = locator.root
  var centerX: Int = 0
  var centerZ: Int = 0
  var ambiguous: Boolean = false
  var domainChain: List<terrasect.sdf.DomainDecoration> = emptyList()
  var layerOps: List<terrasect.sdf.LayerDecoration> = emptyList()
  private var enteredPosition = -1

  fun reset(id: ByteBuffer, cache: RegionsCache? = null) {
    this.id = id
    this.cache = cache
    this.sdf.reset()

    this.region = locator.root
    this.centerX = 0
    this.centerZ = 0
    this.ambiguous = false
    this.domainChain = emptyList()
    this.layerOps = emptyList()
    this.enteredPosition = -1
  }

  fun append(sdf: Sdf2) {
    if (domainChain.isEmpty() && layerOps.isEmpty()) {
      this.sdf.append(sdf)
    } else {
      this.sdf.append(terrasect.sdf.DecoratedSdf(sdf, domainChain, layerOps))
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

    var seed = prefixSeed(locator.seed, id)
    val ops = ArrayList<terrasect.sdf.LayerDecoration>()
    var chain = domainChain
    for (decoration in region.decorations) {
      when (val instance = decoration.instantiate(seed, centerX, centerZ)) {
        is terrasect.sdf.DomainDecoration -> chain = chain + instance
        is terrasect.sdf.LayerDecoration -> ops += instance
      }
      seed = seed * 31 + 17
    }
    domainChain = chain
    layerOps = ops
  }

  fun next(): LocateStep? {
    val strategyId = peekStrategyId() ?: return null
    val strategy = locator.resolve(region, strategyId) ?: return null
    enter(region)
    return strategy.locate(this)
  }

  fun peekStrategyId(): Byte? {
    if (!id.hasRemaining()) {
      return null
    }

    return id.get(id.position())
  }

  fun result(region: Region = this.region, chain: List<Qualifier> = emptyList()): LocatorResult {
    val baked = sdf.bake()
    val distance = baked(centerX, centerZ)
    val offsetX = locator.offsetX
    val offsetZ = locator.offsetZ
    return LocatorResult(
      region = region,
      centerX = centerX - offsetX,
      centerZ = centerZ - offsetZ,
      sdf = if (offsetX == 0 && offsetZ == 0) baked else translate(baked, -offsetX, -offsetZ),
      distance = distance,
      ambiguous = ambiguous,
      chain = chain,
    )
  }
}
