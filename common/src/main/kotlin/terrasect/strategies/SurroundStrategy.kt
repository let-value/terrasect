package terrasect.strategies

import terrasect.definition.*
import terrasect.generation.TraversalStep
import terrasect.sdf.*
import kotlin.math.max

private val discriminator = StrategyId.SURROUND.value

data class SurroundOriginResult(
    var centerX: Double = 0.0,
    var centerZ: Double = 0.0,
    var scale: Double = 1.0,
    var parent: Sdf2 = EmptySdf,
)

class SurroundStrategy(
    val center: Region,
    val surround: Region,
    val scale: Double,
    val smoothing: Double,
) : Strategy {
  val centerSdfRef: ThreadLocal<CenterCellSdf> = ThreadLocal.withInitial { CenterCellSdf() }
  val surroundSdfRef: ThreadLocal<SurroundCellSdf> = ThreadLocal.withInitial { SurroundCellSdf() }

  fun getCachedOrigin(step: TraversalStep): SurroundOriginResult {
    val cache = step.cache ?: return getOrigin(step.sdf.bake(), scale)

    val key = cache.getKey(step.id)
    val cached = cache.surround.getIfPresent(key)
    if (cached != null) {
      return cached
    }

    val origin = getOrigin(step.sdf.bake(), scale)
    cache.surround.put(key, origin)

    return origin
  }

  override fun traverse(step: TraversalStep): TraversalStep {
    step.id.put(discriminator)

    val origin = getCachedOrigin(step)
    val isCenter =
        surroundDistance(
            step.x,
            step.z,
            origin.parent,
            origin.centerX,
            origin.centerZ,
            origin.scale,
            smoothing,
        ) <= 0.0

    step.id.putDouble(origin.centerX)
    step.id.putDouble(origin.centerZ)

    if (isCenter) {
      val sdf = centerSdfRef.get()
      sdf.parent = origin.parent
      sdf.centerX = origin.centerX
      sdf.centerZ = origin.centerZ
      sdf.scale = origin.scale
      sdf.smoothing = smoothing
      step.sdf.append(sdf)
    } else {
      val sdf = surroundSdfRef.get()
      sdf.parent = origin.parent
      sdf.centerX = origin.centerX
      sdf.centerZ = origin.centerZ
      sdf.scale = origin.scale
      sdf.smoothing = smoothing
      step.sdf.append(sdf)
    }

    val distance = step.sdf(step.x, step.z)
    step.distance = max(step.distance, distance)

    step.region = if (isCenter) center else surround

    return step
  }

  companion object {

    fun getOrigin(parentSdf: Sdf2, scale: Double): SurroundOriginResult {
      val bounds = estimateBounds(parentSdf)
      val centerX = (bounds.minX + bounds.maxX) * 0.5
      val centerZ = (bounds.minZ + bounds.maxZ) * 0.5

      val origin = SurroundOriginResult()
      origin.centerX = centerX
      origin.centerZ = centerZ
      origin.scale = scale
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

      val smoothing = -15.0

      val scale = surroundScale(center.budget, center.budget + surround.budget)

      return SurroundStrategy(center, surround, scale, smoothing)
    }
  }
}
