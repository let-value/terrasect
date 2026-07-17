package terrasect.strategies

import java.nio.ByteBuffer
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt
import terrasect.cache.RegionsCache
import terrasect.definition.Region
import terrasect.definition.RegionBuilder
import terrasect.definition.Strategy
import terrasect.definition.StrategySettings
import terrasect.generation.LocateStep
import terrasect.generation.TraversalStep
import terrasect.sdf.Sdf2
import terrasect.sdf.SubdivisionCellSdf
import terrasect.sdf.estimateBounds

@Suppress("ArrayInDataClass")
data class SubdivisionSplit(var axis: Int = 0, var edges: FloatArray = floatArrayOf())

class SubdivisionStrategy(
  override val id: Byte,
  val children: Array<Region>,
  val budgets: LongArray,
  val tiling: Boolean = false,
) : Strategy {
  override val targets = children.toList()
  override val tiled: Boolean
    get() = tiling

  // The repeating stripe pattern is scaled so one period-long square patch realizes every child
  // budget exactly: width_i = budget_i / sqrt(total budget), period = sqrt(total budget).
  val period = sqrt(budgets.sum().coerceAtLeast(1).toDouble()).toFloat()
  val stripeEdges =
    FloatArray(budgets.size + 1).also { edges ->
      var cumulative = 0f
      for (i in budgets.indices) {
        cumulative += budgets[i] / period
        edges[i + 1] = cumulative
      }
    }

  val cellSdfRef: ThreadLocal<SubdivisionCellSdf> = ThreadLocal.withInitial { SubdivisionCellSdf() }

  private fun getCachedSplit(
    id: ByteBuffer,
    parentSdf: Sdf2,
    cache: RegionsCache?,
    originX: Int = 0,
    originZ: Int = 0,
  ): SubdivisionSplit {
    if (cache == null) {
      return getSplit(parentSdf, budgets, originX, originZ)
    }

    val key = cache.getKey(id)
    return cache.subdivision.getOrCompute(key) {
      getSplit(parentSdf, budgets, originX, originZ)
    }
  }

  override fun traverse(step: TraversalStep): TraversalStep {
    if (tiling) {
      return traverseTiled(step)
    }

    val split = getCachedSplit(step.id, step.sdf, step.cache, step.centerX, step.centerZ)
    val v = if (split.axis == 0) step.qx else step.qz
    val index = getChildIndex(v, split)

    writeId(step.id, index)

    val sdf = cellSdfRef.get()
    sdf.axis = split.axis
    sdf.lo = split.edges[index]
    sdf.hi = split.edges[index + 1]
    step.append(sdf)

    val dist = step.sdf(step.x, step.z)
    step.distance = max(step.distance, dist)

    if (split.axis == 0) {
      step.centerX = ((sdf.lo + sdf.hi) / 2f).toInt()
    } else {
      step.centerZ = ((sdf.lo + sdf.hi) / 2f).toInt()
    }
    step.region = children[index]

    return step
  }

  private fun traverseTiled(step: TraversalStep): TraversalStep {
    val periodIndex = floor(step.qx / period).toInt()
    val offset = step.qx - periodIndex * period
    val index = getStripeIndex(offset)

    writeTileId(step.id, periodIndex, index)

    val sdf = cellSdfRef.get()
    applyStripe(sdf, periodIndex, index)
    step.append(sdf)

    val dist = step.sdf(step.x, step.z)
    step.distance = max(step.distance, dist)

    step.centerX = ((sdf.lo + sdf.hi) / 2f).toInt()
    step.region = children[index]

    return step
  }

  private fun applyStripe(sdf: SubdivisionCellSdf, periodIndex: Int, index: Int) {
    val start = periodIndex * period
    sdf.axis = 0
    sdf.lo = start + stripeEdges[index]
    sdf.hi = start + stripeEdges[index + 1]
  }

  private fun getStripeIndex(offset: Float): Int {
    for (i in 1 until stripeEdges.size) {
      if (offset <= stripeEdges[i]) {
        return i - 1
      }
    }
    return stripeEdges.size - 2
  }

  override fun locate(step: LocateStep): LocateStep? {
    if (tiling) {
      return locateTiled(step)
    }

    val originalPosition = step.id.position()
    val index = readId(step.id) ?: return null
    if (index == Strategy.SELF_INDEX) {
      return step
    }
    if (index !in children.indices) {
      return null
    }

    val newPosition = step.id.position()
    step.id.position(originalPosition)
    val split = getCachedSplit(step.id, step.sdf, step.cache, step.centerX, step.centerZ)
    step.id.position(newPosition)

    val sdf = SubdivisionCellSdf()
    sdf.axis = split.axis
    sdf.lo = split.edges[index]
    sdf.hi = split.edges[index + 1]
    step.append(sdf)

    val bounds = estimateBounds(step.sdf, step.centerX, step.centerZ)
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

  private fun locateTiled(step: LocateStep): LocateStep? {
    val tile = readTileId(step.id) ?: return null
    val (periodIndex, index) = tile
    if (index !in children.indices) {
      return null
    }

    val sdf = SubdivisionCellSdf()
    applyStripe(sdf, periodIndex, index)
    step.append(sdf)

    step.centerX = ((sdf.lo + sdf.hi) / 2f).toInt()
    step.region = children[index]

    return step
  }

  override fun resolve(step: LocateStep, child: Region): LocateStep? {
    val index = children.indexOfFirst { it === child }
    if (index < 0) {
      return null
    }

    val position = step.id.position()
    if (tiling) {
      val periodIndex = floor(step.centerX / period).toInt()
      writeTileId(step.id, periodIndex, index)
      step.id.position(position)
      step.ambiguous = true
      return locate(step)
    }

    writeId(step.id, index)
    step.id.position(position)

    return locate(step)
  }

  override fun writeSelf(step: LocateStep, buffer: ByteBuffer) {
    writeId(buffer, Strategy.SELF_INDEX)
  }

  fun writeId(buffer: ByteBuffer, index: Int) {
    buffer.put(id)
    buffer.putInt(index)
  }

  fun writeTileId(buffer: ByteBuffer, periodIndex: Int, index: Int) {
    buffer.put(id)
    buffer.putInt(periodIndex)
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

  fun readTileId(buffer: ByteBuffer): Pair<Int, Int>? {
    try {
      val strategyId = buffer.get()
      if (strategyId != id) {
        return null
      }

      val periodIndex = buffer.getInt()
      val index = buffer.getInt()
      return periodIndex to index
    } catch (_: Exception) {
      return null
    }
  }

  companion object {

    fun getSplit(
      parentSdf: Sdf2,
      budgets: LongArray,
      originX: Int = 0,
      originZ: Int = 0,
    ): SubdivisionSplit {
      val bounds = estimateBounds(parentSdf, originX, originZ)
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
    var tiling = false

    fun tiling(value: Boolean = true) = apply { this.tiling = value }

    override fun build(builder: RegionBuilder, children: Set<Region>): SubdivisionStrategy {
      val sortedChildren = children.sortedByDescending { it.budget }
      val budgets = sortedChildren.map { it.budget }.toLongArray()
      return SubdivisionStrategy(builder.id, sortedChildren.toTypedArray(), budgets, tiling)
    }
  }
}
