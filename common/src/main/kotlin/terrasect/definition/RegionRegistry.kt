package terrasect.definition

object RegionRegistry {
  val drafts = mutableMapOf<String, DraftRegion>()

  fun region(name: String) = drafts.getOrPut(name) { DraftRegion(name) }

  private val visiting = mutableSetOf<String>()

  fun build(name: String, parent: RegionDefinition.Builder? = null): Region {
    val draft = drafts[name] ?: return Region.empty(name)

    // initialize strategy

    if (!visiting.add(name)) {
      return Region.empty(name)
    }

    val builder = draft.copy()
    if (parent !== null) {
      builder.inheritParent(parent)
    }

    val children = draft.children.map { childName -> build(childName, builder) }.toSet()

    val definition = builder.build()

    visiting.remove(name)

    return Region(
        name = draft.name,
        budget = draft.areaBudget ?: 10000,
        children = children,
        climate = definition.climate,
        height = definition.height,
        noise = definition.noise,
        biomes = definition.biomes,
        structures = definition.structures,
        mobs = definition.mobs,
        generationStrategy = definition.generationStrategy,
    )
  }

  class DraftRegion(val name: String) : RegionDefinition.Builder() {
    var areaBudget: Int? = null
    var adjacentTo = setOf<String>()
    var parent: String? = null
    val children = mutableSetOf<String>()
    var originAnchor = false

    fun parent(name: String) = apply { parent = name }

    fun area(budget: Int) = apply { areaBudget = budget * budget }

    fun adjacentTo(vararg regionNames: String) = apply { adjacentTo = regionNames.toSet() }

    fun originAnchor() = apply { originAnchor = true }

    fun child(name: String, consumer: (DraftRegion) -> Unit) = apply {
      region(name).apply(consumer).parent(name)
      children.add(name)
    }
  }
}
