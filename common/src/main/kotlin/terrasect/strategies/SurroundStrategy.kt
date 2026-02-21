package terrasect.strategies

import java.nio.ByteBuffer
import kotlin.math.max
import terrasect.cache.Cache
import terrasect.definition.Region
import terrasect.definition.RegionBuilder
import terrasect.definition.Strategy
import terrasect.definition.StrategySettings
import terrasect.generation.LocateStep
import terrasect.generation.TraversalStep
import terrasect.sdf.*

data class SurroundOriginResult(
    var centerX: Int = 0,
    var centerZ: Int = 0,
    var isCenter: Boolean = false,
    var parent: Sdf2 = EmptySdf,
)

class SurroundStrategy(
    override val id: Byte,
    val center: Region,
    val surround: Region,
    val scale: Float,
    val smoothing: Float,
) : Strategy {
  val centerSdfRef: ThreadLocal<CenterCellSdf> = ThreadLocal.withInitial { CenterCellSdf() }
  val surroundSdfRef: ThreadLocal<SurroundCellSdf> = ThreadLocal.withInitial { SurroundCellSdf() }

  fun getCachedOrigin(id: ByteBuffer, parentSdf: SdfCompose, cache: Cache?): SurroundOriginResult {
    if (cache == null) {
      return getOrigin(parentSdf.bake())
    }

    val key = cache.getKey(id)
    return cache.surround.getOrCompute(key) { getOrigin(parentSdf.bake()) }
  }

  override fun traverse(step: TraversalStep): TraversalStep {
    val origin = getCachedOrigin(step.id, step.sdf, step.cache)
    val isCenter =
        surroundDistance(
            step.x,
            step.z,
            origin.parent,
            origin.centerX,
            origin.centerZ,
            scale,
            smoothing,
        ) <= 0.0

    writeId(step.id, origin, isCenter)

    if (isCenter) {
      val sdf = centerSdfRef.get()
      sdf.parent = origin.parent
      sdf.centerX = origin.centerX
      sdf.centerZ = origin.centerZ
      sdf.scale = scale
      sdf.smoothing = smoothing
      step.sdf.append(sdf)
    } else {
      val sdf = surroundSdfRef.get()
      sdf.parent = origin.parent
      sdf.centerX = origin.centerX
      sdf.centerZ = origin.centerZ
      sdf.scale = scale
      sdf.smoothing = smoothing
      step.sdf.append(sdf)
    }

    val distance = step.sdf(step.x, step.z)
    step.distance = max(step.distance, distance)

    step.region = if (isCenter) center else surround

    return step
  }

  override fun locate(step: LocateStep): LocateStep? {
    val origin = readId(step.id) ?: return null
    val parent = step.sdf.bake()

    if (origin.isCenter) {
      val sdf = CenterCellSdf()
      sdf.parent = parent
      sdf.centerX = origin.centerX
      sdf.centerZ = origin.centerZ
      sdf.scale = scale
      sdf.smoothing = smoothing
      step.sdf.append(sdf)
    } else {
      val sdf = SurroundCellSdf()
      sdf.parent = parent
      sdf.centerX = origin.centerX
      sdf.centerZ = origin.centerZ
      sdf.scale = scale
      sdf.smoothing = smoothing
      step.sdf.append(sdf)
    }

    step.centerX = origin.centerX
    step.centerZ = origin.centerZ
    step.region = if (origin.isCenter) center else surround

    return step
  }

  fun writeId(buffer: ByteBuffer, origin: SurroundOriginResult, isCenter: Boolean) {
    buffer.put(id)
    buffer.putInt(origin.centerX)
    buffer.putInt(origin.centerZ)
    buffer.put(if (isCenter) 0.toByte() else 1.toByte())
  }

  fun readId(buffer: ByteBuffer): SurroundOriginResult? {
    try {
      val strategyId = buffer.get()
      if (strategyId != id) {
        return null
      }

      val centerX = buffer.getInt()
      val centerZ = buffer.getInt()
      val isCenter = buffer.get() == 0.toByte()

      return SurroundOriginResult(centerX, centerZ, isCenter)
    } catch (_: Exception) {
      return null
    }
  }

  companion object {

    fun getOrigin(parentSdf: Sdf2): SurroundOriginResult {
      val bounds = estimateBounds(parentSdf)
      val centerX = (bounds.minX + bounds.maxX) / 2
      val centerZ = (bounds.minZ + bounds.maxZ) / 2

      val origin = SurroundOriginResult()
      origin.centerX = centerX
      origin.centerZ = centerZ
      origin.parent = parentSdf
      return origin
    }

    fun builder(surroundRegionName: String) = Builder(surroundRegionName)
  }

  class Builder(val surroundRegionName: String) : StrategySettings {

    override fun build(builder: RegionBuilder, children: Set<Region>): SurroundStrategy {
      val center =
          children.find { it.name != surroundRegionName }
              ?: Region.empty("${surroundRegionName}_center")

      val surround = builder.registry.buildTree(surroundRegionName)

      val smoothing = -15f

      val scale = surroundScale(center.budget, center.budget + surround.budget)

      return SurroundStrategy(builder.id, center, surround, scale, smoothing)
    }
  }
}
