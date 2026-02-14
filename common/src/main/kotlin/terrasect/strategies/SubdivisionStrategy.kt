package terrasect.strategies

import kotlin.math.max
import terrasect.definition.*
import terrasect.generation.TraversalStep
import terrasect.sdf.Sdf2
import terrasect.sdf.SubdivisionCellSdf
import terrasect.sdf.estimateBounds

private val discriminator = StrategyId.SUBDIVISION.value

@Suppress("ArrayInDataClass")
data class SubdivisionSplit(
    var axis: Int = 0,
    var edges: DoubleArray = doubleArrayOf(),
)

class SubdivisionStrategy(val children: Array<Region>, val budgets: DoubleArray) : Strategy {
  val cellSdfRef: ThreadLocal<SubdivisionCellSdf> = ThreadLocal.withInitial { SubdivisionCellSdf() }

  companion object {
    val splitRef: ThreadLocal<SubdivisionSplit> = ThreadLocal.withInitial { SubdivisionSplit() }

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

      val split = splitRef.get()
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

    fun traverse(step: TraversalStep, settings: SubdivisionStrategy): TraversalStep {
      val split = getSplit(step.sdf, settings.budgets)

      val v = if (split.axis == 0) step.x.toDouble() else step.z.toDouble()
      val index = getChildIndex(v, split)

      step.id.put(discriminator)
      step.id.putInt(index)

      val sdf = settings.cellSdfRef.get()
      sdf.axis = split.axis
      sdf.lo = split.edges[index]
      sdf.hi = split.edges[index + 1]
      step.sdf.append(sdf)

      val dist = sdf(step.x.toDouble(), step.z.toDouble())
      step.distance = max(step.distance, dist)

      step.region = settings.children[index]

      return step
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
