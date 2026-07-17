package terrasect.strategies

import java.nio.ByteBuffer
import kotlin.math.hypot
import kotlin.math.max
import terrasect.cache.RegionsCache
import terrasect.definition.Region
import terrasect.definition.RegionBuilder
import terrasect.definition.Strategy
import terrasect.definition.StrategySettings
import terrasect.generation.LocateStep
import terrasect.generation.TraversalStep
import terrasect.sdf.*

class VoronoiStrategy(override val id: Byte, val children: Array<Region>, val budgets: LongArray) :
  Strategy {
  override val targets = children.toList()

  val cellSdfRef: ThreadLocal<VoronoiCellSdf> = ThreadLocal.withInitial { VoronoiCellSdf() }

  private fun getCachedSites(
    seed: Int,
    id: ByteBuffer,
    parentSdf: Sdf2,
    cache: RegionsCache?,
    originX: Int = 0,
    originZ: Int = 0,
  ): List<Site> {
    if (cache == null) {
      return getSites(seed, parentSdf, budgets, originX, originZ)
    }

    val key = cache.getKey(id)
    return cache.voronoi.getOrCompute(key) { getSites(seed, parentSdf, budgets, originX, originZ) }
  }

  override fun traverse(step: TraversalStep): TraversalStep {
    val seed = getCellSeed(step.traverser.seed, step.id)
    val sites = getCachedSites(seed, step.id, step.sdf, step.cache, step.centerX, step.centerZ)
    val index = getCellIndex(step.x, step.z, sites)

    writeId(step.id, seed, index)

    val sdf = cellSdfRef.get()
    sdf.sites = sites
    sdf.index = index
    step.sdf.append(sdf)

    val dist = step.sdf(step.x, step.z)

    step.distance = max(step.distance, dist)
    step.centerX = sites[index].x
    step.centerZ = sites[index].z
    step.region = children[index]

    return step
  }

  override fun locate(step: LocateStep): LocateStep? {
    val originalPosition = step.id.position()
    val (seed, index) = readId(step.id) ?: return null
    if (index == Strategy.SELF_INDEX) {
      return step
    }
    if (index !in children.indices) {
      return null
    }

    val newPosition = step.id.position()
    step.id.position(originalPosition)
    val sites = getCachedSites(seed, step.id, step.sdf, step.cache, step.centerX, step.centerZ)
    step.id.position(newPosition)

    if (index !in sites.indices) {
      return null
    }

    val sdf = VoronoiCellSdf()
    sdf.sites = sites
    sdf.index = index
    step.sdf.append(sdf)

    val site = sites[index]
    step.centerX = site.x
    step.centerZ = site.z
    step.region = children[index]

    return step
  }

  override fun resolve(step: LocateStep, child: Region): LocateStep? {
    val index = children.indexOfFirst { it === child }
    if (index < 0) {
      return null
    }

    val seed = getCellSeed(step.locator.seed, step.id)
    val position = step.id.position()
    writeId(step.id, seed, index)
    step.id.position(position)

    return locate(step)
  }

  override fun writeSelf(step: LocateStep, buffer: ByteBuffer) {
    writeId(buffer, getCellSeed(step.locator.seed, buffer), Strategy.SELF_INDEX)
  }

  fun writeId(buffer: ByteBuffer, seed: Int, index: Int) {
    buffer.put(id)
    buffer.putInt(seed)
    buffer.putInt(index)
  }

  fun readId(buffer: ByteBuffer): Pair<Int, Int>? {
    try {
      val strategyId = buffer.get()
      if (strategyId != id) {
        return null
      }

      val seed = buffer.getInt()
      val index = buffer.getInt()

      return seed to index
    } catch (_: Exception) {
      return null
    }
  }

  companion object {
    fun getCellSeed(seed: Long, id: ByteBuffer): Int {
      val idPos = id.position()
      var cellSeed = seed
      for (i in 0 until idPos) {
        cellSeed = cellSeed * 31 + id.get(i).toLong()
      }
      return cellSeed.toInt()
    }

    fun getSites(
      cellSeed: Int,
      parentSdf: Sdf2,
      budgets: LongArray,
      originX: Int = 0,
      originZ: Int = 0,
    ): List<Site> {
      val bounds = estimateBounds(parentSdf, originX, originZ)
      return getSites(cellSeed.toLong(), parentSdf, bounds, budgets)
    }

    fun getCellIndex(x: Int, z: Int, sites: List<Site>): Int {
      var closestIndex = 0
      var closestPower = Float.POSITIVE_INFINITY

      for (i in sites.indices) {
        val site = sites[i]
        val dx = x - site.x
        val dz = z - site.z
        val dist = hypot(dx.toDouble(), dz.toDouble()).toFloat()
        val power = dist - site.radius
        if (power < closestPower) {
          closestPower = power
          closestIndex = i
        }
      }

      return closestIndex
    }

    fun builder() = Builder()
  }

  class Builder : StrategySettings {
    override fun build(builder: RegionBuilder, children: Set<Region>): VoronoiStrategy {
      val sortedChildren = children.sortedByDescending { it.budget }
      val budgets = sortedChildren.map { it.budget }.sortedByDescending { it }.toLongArray()
      return VoronoiStrategy(builder.id, sortedChildren.toTypedArray(), budgets)
    }
  }
}
