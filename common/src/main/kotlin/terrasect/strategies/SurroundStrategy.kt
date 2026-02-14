package terrasect.strategies

import terrasect.definition.*
import terrasect.generation.TraversalStep
import terrasect.sdf.*
import kotlin.math.max

private val discriminator = StrategyId.SURROUND.value

class SurroundStrategy(val center: Region, val surround: Region, val scale: Double) : Strategy {
  val cellSdfRef: ThreadLocal<SurroundCellSdf> = ThreadLocal.withInitial { SurroundCellSdf() }

  companion object {
    fun builder(centerRegionName: String) = Builder(centerRegionName)

    fun getDistance(x: Double, z: Double, parentSdf: Sdf2, scale: Double): Double {
      val bounds = estimateBounds(parentSdf)
      val centerX = (bounds.minX + bounds.maxX) * 0.5
      val centerZ = (bounds.minZ + bounds.maxZ) * 0.5
      return surroundInnerDistance(x, z, parentSdf, centerX, centerZ, scale)
    }

    fun traverse(step: TraversalStep, settings: SurroundStrategy): TraversalStep {
      val x = step.x.toDouble()
      val z = step.z.toDouble()

      val distance = getDistance(x, z, step.sdf, settings.scale)
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
