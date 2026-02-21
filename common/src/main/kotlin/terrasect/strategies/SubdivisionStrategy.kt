package terrasect.strategies

import terrasect.cache.Cache
import terrasect.definition.Region
import terrasect.definition.RegionBuilder
import terrasect.definition.Strategy
import terrasect.definition.StrategySettings
import terrasect.generation.LocateStep
import terrasect.generation.TraversalStep
import terrasect.sdf.Sdf2
import terrasect.sdf.SubdivisionCellSdf
import terrasect.sdf.estimateBounds
import java.nio.ByteBuffer
import kotlin.math.max

@Suppress("ArrayInDataClass")
data class SubdivisionSplit(
    var axis: Int = 0,
    var edges: FloatArray = floatArrayOf(),
)

class SubdivisionStrategy(
    override val id: Byte,
    val children: Array<Region>,
    val budgets: LongArray,
) : Strategy {
  val cellSdfRef: ThreadLocal<SubdivisionCellSdf> = ThreadLocal.withInitial { SubdivisionCellSdf() }

  private fun getCachedSplit(id: ByteBuffer, parentSdf: Sdf2, cache: Cache?): SubdivisionSplit {
    if (cache == null) {
      return getSplit(parentSdf, budgets)
    }

    val key = cache.getKey(id)
    return cache.subdivision.getOrCompute(key) { getSplit(parentSdf, budgets) }
  }

  override fun traverse(step: TraversalStep): TraversalStep {
    val split = getCachedSplit(step.id, step.sdf, step.cache)
    val v = if (split.axis == 0) step.x else step.z
    val index = getChildIndex(v, split)

    writeId(step.id, index)

    val sdf = cellSdfRef.get()
    sdf.axis = split.axis
    sdf.lo = split.edges[index]
    sdf.hi = split.edges[index + 1]
    step.sdf.append(sdf)

    val dist = step.sdf(step.x, step.z)
    step.distance = max(step.distance, dist)

    step.region = children[index]

    return step
  }

  override fun locate(step: LocateStep): LocateStep? {
    val originalPosition = step.id.position()
    val index = readId(step.id) ?: return null
    if (index !in children.indices) {
      return null
    }

    val newPosition = step.id.position()
    step.id.position(originalPosition)
    val split = getCachedSplit(step.id, step.sdf, step.cache)
    step.id.position(newPosition)

    val sdf = SubdivisionCellSdf()
    sdf.axis = split.axis
    sdf.lo = split.edges[index]
    sdf.hi = split.edges[index + 1]
    step.sdf.append(sdf)

    val bounds = estimateBounds(step.sdf)
    if (split.axis == 0) {
      step.centerX = ((sdf.lo + sdf.hi) / 2f).toInt()
      step.centerZ = (bounds.minZ + bounds.maxZ) / 2
    } else {
      step.centerX = (bounds.minX + bounds.maxX) / 2
      step.centerZ = ((sdf.lo + sdf.hi) / 2f).toInt()
    }

    step.region = children[index]

    return step
  }

  fun writeId(buffer: ByteBuffer, index: Int) {
    buffer.put(id)
    buffer.putInt(index)
  }

  fun readId(buffer: ByteBuffer): Int? {
    try {
      val strategyId = buffer.get()
      if (strategyId != id) {
        return null
      }

      val index = buffer.getInt()
      return index
    } catch (_: Exception) {
      return null
    }
  }

  companion object {

    fun getSplit(parentSdf: Sdf2, budgets: LongArray): SubdivisionSplit {
      val bounds = estimateBounds(parentSdf)
      val splitX = bounds.width >= bounds.height
      val axis = if (splitX) 0 else 1
      val axisMin = if (splitX) bounds.minX else bounds.minZ
      val axisMax = if (splitX) bounds.maxX else bounds.maxZ
      val axisLen = axisMax - axisMin
      val totalBudget = budgets.sum()

      val edges = FloatArray(budgets.size + 1)
      edges[0] = axisMin.toFloat()
      var cumulative = 0f
      for (i in budgets.indices) {
        cumulative += budgets[i]
        edges[i + 1] = axisMin + axisLen * (cumulative / totalBudget)
      }
      edges[budgets.size] = axisMax.toFloat()

      val split = SubdivisionSplit()
      split.edges = edges
      split.axis = axis

      return split
    }

    fun getChildIndex(v: Int, split: SubdivisionSplit): Int {
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
    override fun build(builder: RegionBuilder, children: Set<Region>): SubdivisionStrategy {
      val sortedChildren = children.sortedByDescending { it.budget }
      val budgets = sortedChildren.map { it.budget }.toLongArray()
      return SubdivisionStrategy(builder.id, sortedChildren.toTypedArray(), budgets)
    }
  }
}
