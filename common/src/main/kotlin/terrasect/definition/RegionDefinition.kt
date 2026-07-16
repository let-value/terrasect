package terrasect.definition

import kotlin.math.max

open class RegionDefinition(
  val registry: RegionRegistry,
  val name: String,
  val originAnchor: Boolean = false,
  var budget: Long,
  val strategy: StrategySettings,
  var parent: String? = null,
  val children: MutableSet<String>? = null,
  val climate: ClimateConstraints? = null,
  val height: HeightConstraints? = null,
  val noise: NoiseConstraints? = null,
  val biomes: SelectionConstraints? = null,
  val structures: StructureConstraints? = null,
  val mobs: SelectionConstraints? = null,
  val loot: SelectionConstraints? = null,
)

open class RegionBuilder(val id: Byte, val registry: RegionRegistry, var name: String) {
  var originAnchor = false
  var radius: Long? = null
  var budget: Long? = null
  var strategy: StrategySettings? = null
  var parent: String? = null
  val childrenLazy = lazy { mutableSetOf<String>() }
  val children by childrenLazy
  val climateLazyBuilder = lazy { ClimateConstraints.builder() }
  val climateBuilder by climateLazyBuilder
  val heightLazyBuilder = lazy { HeightConstraints.builder() }
  val heightBuilder by heightLazyBuilder
  val noiseLazyBuilder = lazy { NoiseConstraints.builder() }
  val noiseBuilder by noiseLazyBuilder
  val biomesLazyBuilder = lazy { SelectionConstraints.builder() }
  val biomesBuilder by biomesLazyBuilder
  val structuresLazyBuilder = lazy { StructureConstraints.builder() }
  val structuresBuilder by structuresLazyBuilder
  val mobsLazyBuilder = lazy { SelectionConstraints.builder() }
  val mobsBuilder by mobsLazyBuilder
  val lootLazyBuilder = lazy { SelectionConstraints.builder() }
  val lootBuilder by lootLazyBuilder

  fun radius(radius: Long) = apply {
    this.radius = radius
    this.budget = radius * radius
  }

  fun budget(budget: Long) = apply {
    this.radius = null
    this.budget = budget
  }

  fun originAnchor() = apply { originAnchor = true }

  fun strategy(strategy: StrategySettings) = apply { this.strategy = strategy }

  fun parent(name: String) = apply {
    parent = name
    registry.region(name).children.add(this.name)
  }

  fun child(name: String, consumer: (RegionBuilder) -> Unit) = apply {
    this.registry.region(name).apply(consumer).parent(name)
    children.add(name)
  }

  inline fun climate(consumer: ClimateConstraints.Builder.() -> Unit) = apply {
    climateBuilder.apply(consumer)
  }

  inline fun height(consumer: HeightConstraints.Builder.() -> Unit) = apply {
    heightBuilder.apply(consumer)
  }

  inline fun noise(consumer: NoiseConstraints.Builder.() -> Unit) = apply {
    noiseBuilder.apply(consumer)
  }

  inline fun biomes(consumer: SelectionConstraints.Builder.() -> Unit) = apply {
    biomesBuilder.apply(consumer)
  }

  inline fun structures(consumer: StructureConstraints.Builder.() -> Unit) = apply {
    structuresBuilder.apply(consumer)
  }

  inline fun mobs(consumer: SelectionConstraints.Builder.() -> Unit) = apply {
    mobsBuilder.apply(consumer)
  }

  inline fun loot(consumer: SelectionConstraints.Builder.() -> Unit) = apply {
    lootBuilder.apply(consumer)
  }

  fun copy(): RegionBuilder {
    return RegionBuilder(this.id, this.registry, this.name).also {
      it.radius = this.radius
      it.budget = this.budget
      it.inheritParent(this)
      if (this.structuresLazyBuilder.isInitialized()) {
        it.structuresBuilder.copyForced(this.structuresBuilder)
      }
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
    if (parent.lootLazyBuilder.isInitialized()) {
      this.lootBuilder.inheritParent(parent.lootBuilder)
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
      parent = this.parent,
      children = if (this.childrenLazy.isInitialized()) this.children else null,
      climate = if (this.climateLazyBuilder.isInitialized()) climateBuilder.build() else null,
      height = if (this.heightLazyBuilder.isInitialized()) heightBuilder.build() else null,
      noise = if (this.noiseLazyBuilder.isInitialized()) noiseBuilder.build() else null,
      biomes = if (this.biomesLazyBuilder.isInitialized()) biomesBuilder.build() else null,
      structures =
        if (this.structuresLazyBuilder.isInitialized()) structuresBuilder.build() else null,
      mobs = if (this.mobsLazyBuilder.isInitialized()) mobsBuilder.build() else null,
      loot = if (this.lootLazyBuilder.isInitialized()) lootBuilder.build() else null,
    )
  }
}
