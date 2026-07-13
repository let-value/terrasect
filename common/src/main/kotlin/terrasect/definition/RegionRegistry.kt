package terrasect.definition

class RegionRegistry {
  val drafts = mutableMapOf<String, RegionBuilder>()
  val dimensionRoots = mutableMapOf<String, String>()
  private var sequence: Byte = Byte.MIN_VALUE

  fun region(name: String) = drafts.getOrPut(name) { RegionBuilder(nextStrategyId(), this, name) }

  fun setRoot(dimensionId: String, name: String) = dimensionRoots.set(dimensionId, name)

  fun getRoot(dimensionId: String): String? = dimensionRoots[dimensionId]

  private val visiting = mutableSetOf<String>()

  fun buildTree(name: String, parent: RegionBuilder? = null): Region {
    val draft = drafts[name] ?: return Region.empty(name)

    if (!visiting.add(name)) {
      return Region.empty(name)
    }

    try {
      val builder = draft.copy()
      if (parent !== null) {
        builder.inheritParent(parent)
      }

      val children = draft.children.map { childName -> buildTree(childName, builder) }.toSet()

      val definition = builder.build(children)

      return Region(
        name = definition.name,
        budget = definition.budget,
        children = children,
        strategy = definition.strategy.build(builder, children),
        climate = definition.climate,
        height = definition.height,
        noise = definition.noise,
        biomes = definition.biomes,
        structures = definition.structures,
        mobs = definition.mobs,
        loot = definition.loot,
      )
    } finally {
      visiting.remove(name)
    }
  }

  fun resolveDraft(name: String): RegionBuilder {
    val draft = drafts[name] ?: return RegionBuilder(nextStrategyId(), this, name)

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

  private fun nextStrategyId(): Byte {
    if (sequence >= Byte.MAX_VALUE) {
      error("Too many regions with strategies registered. Max supported is 256.")
    }

    return sequence++
  }
}
