package terrasect.definition

class RegionDefinition(
    val climate: ClimateSettings? = null,
    val height: HeightConstraints? = null,
    val noise: NoiseConstraints? = null,
    val biomes: SelectionRules? = null,
    val structures: SelectionRules? = null,
    val mobs: SelectionRules? = null,
    val generationStrategy: GenerationStrategy? = null,
) {

  fun inheritParent(parent: RegionDefinition?): RegionDefinition {
    if (parent == null) return this
    return RegionDefinition(
        climate = this.climate?.inheritParent(parent.climate) ?: parent.climate,
        height = this.height?.inheritParent(parent.height) ?: parent.height,
        noise = this.noise?.inheritParent(parent.noise) ?: parent.noise,
        biomes = this.biomes?.inheritParent(parent.biomes) ?: parent.biomes,
        structures = this.structures?.inheritParent(parent.structures) ?: parent.structures,
        mobs = this.mobs?.inheritParent(parent.mobs) ?: parent.mobs,
        generationStrategy = this.generationStrategy ?: parent.generationStrategy,
    )
  }

  companion object {
    fun builder() = Builder()
  }

  class Builder {
    val climateBuilder: ClimateSettings.Builder by lazy { ClimateSettings.builder() }
    val heightBuilder: HeightConstraints.Builder by lazy { HeightConstraints.builder() }

    inline fun climate(consumer: ClimateSettings.Builder.() -> Unit) = apply {
      consumer(climateBuilder)
    }

    inline fun height(consumer: HeightConstraints.Builder.() -> Unit) = apply {
      consumer(heightBuilder)
    }
  }
}
