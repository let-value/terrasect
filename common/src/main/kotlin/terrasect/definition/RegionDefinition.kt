package terrasect.definition

import kotlin.math.max

open class RegionDefinition(
    val registry: RegionRegistry,
    val name: String,
    val originAnchor: Boolean = false,
    var budget: Long,
    val strategy: StrategySettings,
    var adjacentTo: Set<String>? = null,
    var parent: String? = null,
    val children: MutableSet<String>? = null,
    val climate: ClimateSettings? = null,
    val height: HeightConstraints? = null,
    val noise: NoiseConstraints? = null,
    val biomes: SelectionRules? = null,
    val structures: SelectionRules? = null,
    val mobs: SelectionRules? = null,
)

open class RegionBuilder(val registry: RegionRegistry, var name: String) {
  var originAnchor = false
  var budget: Long? = null
  var strategy: StrategySettings? = null
  val adjacentToLazy = lazy { setOf<String>() }
  val adjacentTo by adjacentToLazy
  var parent: String? = null
  val childrenLazy = lazy { mutableSetOf<String>() }
  val children by childrenLazy
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

  fun area(budget: Long) = apply { this.budget = budget * budget }

  fun originAnchor() = apply { originAnchor = true }

  fun strategy(strategy: StrategySettings) = apply { this.strategy = strategy }

  fun parent(name: String) = apply {
    parent = name
    registry.region(name).children.add(this.name)
  }

  fun adjacentTo(vararg regionNames: String) = apply { adjacentTo.plus(regionNames) }

  fun child(name: String, consumer: (RegionBuilder) -> Unit) = apply {
    this.registry.region(name).apply(consumer).parent(name)
    children.add(name)
  }

  inline fun climate(consumer: ClimateSettings.Builder.() -> Unit) = apply {
    climateBuilder.apply(consumer)
  }

  inline fun height(consumer: HeightConstraints.Builder.() -> Unit) = apply {
    heightBuilder.apply(consumer)
  }

  inline fun noise(consumer: NoiseConstraints.Builder.() -> Unit) = apply {
    noiseBuilder.apply(consumer)
  }

  inline fun biomes(consumer: SelectionRules.Builder.() -> Unit) = apply {
    biomesBuilder.apply(consumer)
  }

  inline fun structures(consumer: SelectionRules.Builder.() -> Unit) = apply {
    structuresBuilder.apply(consumer)
  }

  inline fun mobs(consumer: SelectionRules.Builder.() -> Unit) = apply {
    mobsBuilder.apply(consumer)
  }

  fun copy(): RegionBuilder {
    return RegionBuilder(this.registry, this.name).also {
      it.budget = this.budget
      it.inheritParent(this)
    }
  }

  fun inheritParent(parent: RegionBuilder) = apply {
    this.parent = parent.name

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
    if (this.strategy == null) {
      this.strategy = parent.strategy
    }
  }

  fun build(children: Set<Region>? = null): RegionDefinition {
    val budget = children?.sumOf { it.budget }?.let { max(this.budget ?: 0, it) }

    return RegionDefinition(
        registry = this.registry,
        name = this.name,
        originAnchor = this.originAnchor,
        budget = budget ?: (this.budget ?: 0),
        strategy = this.strategy ?: Strategy.voronoi(),
        adjacentTo = if (this.adjacentToLazy.isInitialized()) adjacentTo else null,
        parent = this.parent,
        children = if (this.childrenLazy.isInitialized()) this.children else null,
        climate = if (this.climateLazyBuilder.isInitialized()) climateBuilder.build() else null,
        height = if (this.heightLazyBuilder.isInitialized()) heightBuilder.build() else null,
        noise = if (this.noiseLazyBuilder.isInitialized()) noiseBuilder.build() else null,
        biomes = if (this.biomesLazyBuilder.isInitialized()) biomesBuilder.build() else null,
        structures =
            if (this.structuresLazyBuilder.isInitialized()) structuresBuilder.build() else null,
        mobs = if (this.mobsLazyBuilder.isInitialized()) mobsBuilder.build() else null,
    )
  }
}
