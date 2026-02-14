package terrasect.strategies

import terrasect.definition.*
import terrasect.generation.TraversalStep
import terrasect.sdf.Site
import terrasect.sdf.VoronoiCellSdf
import terrasect.sdf.estimateBounds
import terrasect.sdf.getSites
import kotlin.math.hypot
import kotlin.math.max

private val discriminator = StrategyId.VORONOI.value

class VoronoiStrategy(val children: Array<Region>, val budgets: DoubleArray) : Strategy {
  val cellSdfRef: ThreadLocal<VoronoiCellSdf> = ThreadLocal.withInitial { VoronoiCellSdf() }

  companion object {

    fun builder() = Builder()

    fun getSites(step: TraversalStep, settings: VoronoiStrategy): List<Site> {
      val bounds = estimateBounds(step.sdf)

      val idPos = step.id.position()
      var seed = step.traverse.seed
      for (i in 0 until idPos) {
        seed = seed * 31 + step.id.get(i).toLong()
      }

      return getSites(seed, step.sdf, bounds, settings.budgets)
    }

    fun getClosesIndex(x: Double, z: Double, sites: List<Site>): Int {
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

    fun traverse(step: TraversalStep, settings: VoronoiStrategy): TraversalStep {
      val x = step.x.toDouble()
      val z = step.z.toDouble()

      val sites = getSites(step, settings)
      val closestIndex = getClosesIndex(x, z, sites)

      step.id.put(discriminator)
      step.id.putInt(closestIndex)

      val sdf = settings.cellSdfRef.get()
      sdf.sites = sites
      sdf.index = closestIndex
      step.sdf.append(sdf)

      val dist = sdf(x, z)

      step.distance = max(step.distance, dist)
      step.region = settings.children[closestIndex]

      return step
    }
  }

  class Builder() : StrategySettings {

    override fun build(definition: RegionDefinition, children: Set<Region>): VoronoiStrategy {
      val sortedChildren = children.sortedByDescending { it.budget }
      val budgets = sortedChildren.map { it.budget }.sortedByDescending { it }.toDoubleArray()
      return VoronoiStrategy(sortedChildren.toTypedArray(), budgets)
    }
  }
}
