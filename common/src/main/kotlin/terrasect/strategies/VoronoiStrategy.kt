package terrasect.strategies

import terrasect.definition.*
import terrasect.generation.TraversalStep
import terrasect.sdf.estimateBounds
import terrasect.sdf.getSites

private val discriminator = StrategyId.VORONOI.value

class VoronoiStrategy(val budgets: DoubleArray) : Strategy {

  companion object {

    fun builder() = Builder()

    fun traverse(step: TraversalStep, settings: VoronoiStrategy): TraversalStep {

      val bounds = estimateBounds(step.sdf)
      val sites = getSites(step.traverse.seed, step.sdf, bounds, settings.budgets)

      return step
    }
  }

  class Builder() : StrategySettings {

    override fun build(definition: RegionDefinition, children: Set<Region>): VoronoiStrategy {
      val budgets = children.map { it.budget }.toDoubleArray()
      return VoronoiStrategy(budgets)
    }
  }
}
