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
