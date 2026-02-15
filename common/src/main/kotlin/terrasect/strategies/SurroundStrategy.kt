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

  override fun traverse(step: TraversalStep): TraversalStep {
    val origin = getOrigin(step.sdf.bake(), scale)
    val isCenter = getDistance(step.x, step.z, origin, step.sdf, smoothing) <= 0.0

    step.id.put(discriminator)
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

    val originRef: ThreadLocal<SurroundOriginResult> =
        ThreadLocal.withInitial { SurroundOriginResult() }

    fun getOrigin(parentSdf: Sdf2, scale: Double): SurroundOriginResult {
      val bounds = estimateBounds(parentSdf)
      val centerX = (bounds.minX + bounds.maxX) * 0.5
      val centerZ = (bounds.minZ + bounds.maxZ) * 0.5

      val origin = originRef.get()
      origin.centerX = centerX
      origin.centerZ = centerZ
      origin.scale = scale
      origin.parent = parentSdf
      return origin
    }

    fun getDistance(
        x: Double,
        z: Double,
        origin: SurroundOriginResult,
        parentSdf: Sdf2,
        smoothing: Double,
    ): Double {
      return surroundDistance(
          x,
          z,
          parentSdf,
          origin.centerX,
          origin.centerZ,
          origin.scale,
          smoothing,
      )
    }

    fun builder(surroundRegionName: String) = Builder(surroundRegionName)
  }

  class Builder(val surroundRegionName: String) : StrategySettings {

    override fun build(definition: RegionDefinition, children: Set<Region>): SurroundStrategy {
      val center =
          children.find { it.name != surroundRegionName }
              ?: Region.empty("${surroundRegionName}_center")

      val surround = definition.registry.build(surroundRegionName)

      val smoothing = -15.0

      val scale = surroundScale(center.budget, center.budget + surround.budget)

      return SurroundStrategy(center, surround, scale, smoothing)
    }
  }
}
