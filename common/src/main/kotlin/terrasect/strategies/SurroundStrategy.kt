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
)

class SurroundStrategy(val center: Region, val surround: Region, val scale: Double) : Strategy {
  val cellSdfRef: ThreadLocal<SurroundCellSdf> = ThreadLocal.withInitial { SurroundCellSdf() }

  companion object {
    fun builder(centerRegionName: String) = Builder(centerRegionName)

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
      return origin
    }

    fun getDistance(x: Double, z: Double, origin: SurroundOriginResult, parentSdf: Sdf2): Double {
      return surroundInnerDistance(x, z, parentSdf, origin.centerX, origin.centerZ, origin.scale)
    }

    fun traverse(step: TraversalStep, settings: SurroundStrategy): TraversalStep {
      val x = step.x.toDouble()
      val z = step.z.toDouble()

      val origin = getOrigin(step.sdf, settings.scale)
      val distance = getDistance(x, z, origin, step.sdf)
      val isCenter = distance <= 0.0

      step.id.put(discriminator)
      step.id.put(if (isCenter) 0.toByte() else 1.toByte())

      val sdf = settings.cellSdfRef.get()
      sdf.innerDistance = distance
      sdf.isCenter = isCenter
      step.sdf.append(sdf)

      val dist = sdf(x, z)
      step.distance = max(step.distance, dist)

      step.region = if (isCenter) settings.center else settings.surround

      return step
    }
  }

  class Builder(val centerRegionName: String) : StrategySettings {

    override fun build(definition: RegionDefinition, children: Set<Region>): SurroundStrategy {
      val center =
          children.find { it.name == centerRegionName }
              ?: Region.empty("${centerRegionName}_center")
      val surround =
          children.find { it.name != centerRegionName }
              ?: Region.empty("${centerRegionName}_surround")

      val scale = surroundScale(center.budget, surround.budget)

      return SurroundStrategy(center, surround, scale)
    }
  }
}
