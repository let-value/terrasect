package terrasect.strategies

import terrasect.definition.*
import terrasect.generation.TraversalStep
import terrasect.sdf.*
import java.nio.ByteBuffer
import kotlin.math.hypot
import kotlin.math.max

private val discriminator = StrategyId.VORONOI.value

class VoronoiStrategy(val children: Array<Region>, val budgets: DoubleArray) : Strategy {
  val cellSdfRef: ThreadLocal<VoronoiCellSdf> = ThreadLocal.withInitial { VoronoiCellSdf() }

  fun getCachedSites(seed: Int, step: TraversalStep): List<Site> {
    val cache = step.cache ?: return getSites(seed, step.sdf, budgets)

    val key = cache.getKey(step.id)
    val cached = cache.voronoi.getIfPresent(key)
    if (cached != null) {
      return cached
    }

    val sites = getSites(seed, step.sdf, budgets)
    cache.voronoi.put(key, sites)

    return sites
  }

  override fun traverse(step: TraversalStep): TraversalStep {
    val x = step.x
    val z = step.z

    step.id.put(discriminator)

    val seed = getCellSeed(step.traverse.seed, step.id)
    step.id.putInt(seed)

    val sites = getCachedSites(seed, step)
    val index = getCellIndex(x, z, sites)

    step.id.putInt(index)

    val sdf = cellSdfRef.get()
    sdf.sites = sites
    sdf.index = index
    step.sdf.append(sdf)

    val dist = sdf(x, z)

    step.distance = max(step.distance, dist)
    step.region = children[index]

    return step
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

    fun getSites(cellSeed: Int, parentSdf: Sdf2, budgets: DoubleArray): List<Site> {
      val bounds = estimateBounds(parentSdf)
      return getSites(cellSeed.toLong(), parentSdf, bounds, budgets)
    }

    fun getCellIndex(x: Double, z: Double, sites: List<Site>): Int {
      var closestIndex = 0
      var closestPower = Double.POSITIVE_INFINITY

      for (i in sites.indices) {
        val site = sites[i]
        val dx = x - site.x
        val dz = z - site.z
        val dist = hypot(dx, dz)
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
      val budgets = sortedChildren.map { it.budget }.sortedByDescending { it }.toDoubleArray()
      return VoronoiStrategy(sortedChildren.toTypedArray(), budgets)
    }
  }
}
