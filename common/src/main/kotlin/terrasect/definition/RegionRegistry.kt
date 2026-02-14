package terrasect.definition

class RegionRegistry {
  val drafts = mutableMapOf<String, RegionBuilder>()
  val dimensionRoots = mutableMapOf<String, String>()

  fun region(name: String) = drafts.getOrPut(name) { RegionBuilder(this, name) }

  fun setRoot(dimensionId: String, name: String) = dimensionRoots.set(dimensionId, name)

  fun getRoot(dimensionId: String): String? = dimensionRoots[dimensionId]

  private val visiting = mutableSetOf<String>()

  fun buildTree(name: String, parent: RegionBuilder? = null): Region {
    val draft = drafts[name] ?: return Region.empty(name)

    if (!visiting.add(name)) {
      return Region.empty(name)
    }

    val builder = draft.copy()
    if (parent !== null) {
      builder.inheritParent(parent)
    }

    val children = draft.children.map { childName -> buildTree(childName, builder) }.toSet()

    val definition = builder.build(children)

    visiting.remove(name)

    return Region(
        name = definition.name,
        budget = definition.budget,
        children = children,
        strategy = definition.strategy.build(definition, children),
        climate = definition.climate,
        height = definition.height,
        noise = definition.noise,
        biomes = definition.biomes,
        structures = definition.structures,
        mobs = definition.mobs,
    )
  }

  fun resolveDraft(name: String): RegionBuilder {
    val draft = drafts[name] ?: return RegionBuilder(this, name)

    val builder = draft.copy()
    if (draft.parent != null) {
      val parentDraft = resolveDraft(draft.parent!!)
      builder.inheritParent(parentDraft)
    }

    return builder
  }

  fun build(name: String): Region {
    val draft = resolveDraft(name)

    return buildTree(name, draft)
  }
}
