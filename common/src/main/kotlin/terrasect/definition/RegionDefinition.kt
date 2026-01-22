package terrasect.definition

open class RegionDefinition(
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

  open class Builder {
    val climateLazyBuilder = lazy { ClimateSettings.builder() }
    val climateBuilder by climateLazyBuilder
    val heightLazyBuilder = lazy { HeightConstraints.builder() }
    val heightBuilder by heightLazyBuilder
    val noiseLazyBuilder = lazy { NoiseConstraints.builder() }
    val noiseBuilder by noiseLazyBuilder
    val biomesLazyBuilder = lazy { SelectionRules.builder() }
    val biomesBuilder by biomesLazyBuilder
    val structuresLazyBuilder = lazy { SelectionRules.builder() }
    val structuresBuilder by structuresLazyBuilder
    val mobsLazyBuilder = lazy { SelectionRules.builder() }
    val mobsBuilder by mobsLazyBuilder
    var generationStrategy: GenerationStrategy? = null

    inline fun climate(consumer: (ClimateSettings.Builder) -> Unit) = apply {
      consumer(climateBuilder)
    }

    inline fun height(consumer: (HeightConstraints.Builder) -> Unit) = apply {
      consumer(heightBuilder)
    }

    inline fun noise(consumer: (NoiseConstraints.Builder) -> Unit) = apply {
      consumer(noiseBuilder)
    }

    inline fun biomes(consumer: (SelectionRules.Builder) -> Unit) = apply {
      consumer(biomesBuilder)
    }

    inline fun structures(consumer: (SelectionRules.Builder) -> Unit) = apply {
      consumer(structuresBuilder)
    }

    inline fun mobs(consumer: (SelectionRules.Builder) -> Unit) = apply { consumer(mobsBuilder) }

    fun generationStrategy(strategy: GenerationStrategy) = apply {
      this.generationStrategy = strategy
    }

    fun inheritParent(parent: Builder) = apply {
      if (parent.climateLazyBuilder.isInitialized()) {
        this.climateBuilder.inheritParent(parent.climateBuilder)
      }
      if (parent.heightLazyBuilder.isInitialized()) {
        this.heightBuilder.inheritParent(parent.heightBuilder)
      }
      if (parent.noiseLazyBuilder.isInitialized()) {
        this.noiseBuilder.inheritParent(parent.noiseBuilder)
      }
      if (parent.biomesLazyBuilder.isInitialized()) {
        this.biomesBuilder.inheritParent(parent.biomesBuilder)
      }
      if (parent.structuresLazyBuilder.isInitialized()) {
        this.structuresBuilder.inheritParent(parent.structuresBuilder)
      }
      if (parent.mobsLazyBuilder.isInitialized()) {
        this.mobsBuilder.inheritParent(parent.mobsBuilder)
      }
      if (this.generationStrategy == null) {
        this.generationStrategy = parent.generationStrategy
      }
    }

    fun build(): RegionDefinition {
      return RegionDefinition(
          climate = if (this.climateLazyBuilder.isInitialized()) climateBuilder.build() else null,
          height = if (this.heightLazyBuilder.isInitialized()) heightBuilder.build() else null,
          noise = if (this.noiseLazyBuilder.isInitialized()) noiseBuilder.build() else null,
          biomes = if (this.biomesLazyBuilder.isInitialized()) biomesBuilder.build() else null,
          structures =
              if (this.structuresLazyBuilder.isInitialized()) structuresBuilder.build() else null,
          mobs = if (this.mobsLazyBuilder.isInitialized()) mobsBuilder.build() else null,
          generationStrategy = this.generationStrategy,
      )
    }
  }
}
