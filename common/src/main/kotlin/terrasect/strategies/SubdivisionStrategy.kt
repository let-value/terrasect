package terrasect.strategies

import terrasect.definition.*
import terrasect.generation.TraversalStep
import terrasect.sdf.Sdf2
import terrasect.sdf.SubdivisionCellSdf
import terrasect.sdf.estimateBounds
import kotlin.math.max

private val discriminator = StrategyId.SUBDIVISION.value

@Suppress("ArrayInDataClass")
data class SubdivisionSplit(
    var axis: Int = 0,
    var edges: DoubleArray = doubleArrayOf(),
)

class SubdivisionStrategy(val children: Array<Region>, val budgets: DoubleArray) : Strategy {
  val cellSdfRef: ThreadLocal<SubdivisionCellSdf> = ThreadLocal.withInitial { SubdivisionCellSdf() }

  fun getCachedSplit(step: TraversalStep): SubdivisionSplit {
    val cache = step.cache ?: return getSplit(step.sdf, budgets)

    val key = cache.getKey(step.id)
    val cached = cache.subdivision.getIfPresent(key)
    if (cached != null) {
      return cached
    }

    val split = getSplit(step.sdf, budgets)
    cache.subdivision.put(key, split)

    return split
  }

  override fun traverse(step: TraversalStep): TraversalStep {
    step.id.put(discriminator)

    val split = getCachedSplit(step)
    val v = if (split.axis == 0) step.x else step.z
    val index = getChildIndex(v, split)

    step.id.putInt(index)

    val sdf = cellSdfRef.get()
    sdf.axis = split.axis
    sdf.lo = split.edges[index]
    sdf.hi = split.edges[index + 1]
    step.sdf.append(sdf)

    val dist = sdf(step.x, step.z)
    step.distance = max(step.distance, dist)

    step.region = children[index]

    return step
  }

  companion object {

    fun getSplit(parentSdf: Sdf2, budgets: DoubleArray): SubdivisionSplit {
      val bounds = estimateBounds(parentSdf)
      val splitX = bounds.width >= bounds.height
      val axis = if (splitX) 0 else 1
      val axisMin = if (splitX) bounds.minX else bounds.minZ
      val axisMax = if (splitX) bounds.maxX else bounds.maxZ
      val axisLen = axisMax - axisMin
      val totalBudget = budgets.sum()

      val edges = DoubleArray(budgets.size + 1)
      edges[0] = axisMin
      var cumulative = 0.0
      for (i in budgets.indices) {
        cumulative += budgets[i]
        edges[i + 1] = axisMin + axisLen * (cumulative / totalBudget)
      }
      edges[budgets.size] = axisMax

      val split = SubdivisionSplit()
      split.edges = edges
      split.axis = axis

      return split
    }

    fun getChildIndex(v: Double, split: SubdivisionSplit): Int {
      for (i in 1 until split.edges.size) {
        if (v <= split.edges[i]) {
          return i - 1
        }
      }
      return split.edges.size - 2
    }

    fun builder() = Builder()
  }

  class Builder : StrategySettings {
    override fun build(definition: RegionDefinition, children: Set<Region>): SubdivisionStrategy {
      val sortedChildren = children.sortedByDescending { it.budget }
      val budgets = sortedChildren.map { it.budget }.toDoubleArray()
      return SubdivisionStrategy(sortedChildren.toTypedArray(), budgets)
    }
  }
}
