package terrasect.strategies

import terrasect.definition.*
import terrasect.generation.TraversalStep
import terrasect.sdf.Site
import terrasect.sdf.VoronoiCellSdf
import terrasect.sdf.estimateBounds
import terrasect.sdf.getSites
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sqrt

private val discriminator = StrategyId.VORONOI.value

class VoronoiStrategy(val children: Array<Region>, val budgets: DoubleArray) : Strategy {
  val cellSdfRef: ThreadLocal<VoronoiCellSdf> = ThreadLocal.withInitial { VoronoiCellSdf() }

  companion object {

    fun builder() = Builder()

    fun getSites(step: TraversalStep, settings: VoronoiStrategy): List<Site> {
      val bounds = estimateBounds(step.sdf)

      val idPos = step.id.position()
      var cellSeed = step.traverse.seed
      for (i in 0 until idPos) {
        cellSeed = cellSeed * 31 + step.id.get(i).toLong()
      }

      return getSites(cellSeed, step.sdf, bounds, settings.budgets)
    }

    fun getClosesIndex(x: Double, z: Double, sites: List<Site>): Int {
      var closestIndex = 0
      var closestPower = Double.POSITIVE_INFINITY

      for (i in sites.indices) {
        val site = sites[i]
        val dx = x - site.x
        val dz = z - site.z
        val dist = sqrt(dx * dx + dz * dz)
        val power = dist - site.radius
        if (power < closestPower) {
          closestPower = power
          closestIndex = i
        }
      }

      return closestIndex
    }

    fun traverse(step: TraversalStep, settings: VoronoiStrategy): TraversalStep {
      val sites = getSites(step, settings)

      val x = step.x.toDouble()
      val z = step.z.toDouble()

      val closestIndex = getClosesIndex(x, z, sites)

      step.id.put(discriminator)
      step.id.putInt(closestIndex)

      val sdf = settings.cellSdfRef.get()
      sdf.sites = sites
      sdf.cellIndex = closestIndex
      step.sdf.append(sdf)

      val cell = sites[closestIndex]
      val cdx = x - cell.x
      val cdz = z - cell.z
      val cellDist = sqrt(cdx * cdx + cdz * cdz)
      val cellPower = cellDist - cell.radius

      var minEdgeDist = Double.POSITIVE_INFINITY
      for (j in sites.indices) {
        if (j == closestIndex) continue
        val other = sites[j]
        val odx = x - other.x
        val odz = z - other.z
        val otherDist = sqrt(odx * odx + odz * odz)
        val otherPower = otherDist - other.radius
        val edgeDist = (otherPower - cellPower) / 2.0
        if (edgeDist < minEdgeDist) minEdgeDist = edgeDist
      }

      step.distance = max(step.distance, -minEdgeDist)
      step.region = settings.children[closestIndex]

      return step
    }
  }

  class Builder() : StrategySettings {

    override fun build(definition: RegionDefinition, children: Set<Region>): VoronoiStrategy {
      val sortedChildren = children.sortedByDescending { it.budget }
      val budgets =
          sortedChildren
              .map { it.budget * it.budget * PI }
              .sortedByDescending { it }
              .toDoubleArray()
      return VoronoiStrategy(sortedChildren.toTypedArray(), budgets)
    }
  }
}
