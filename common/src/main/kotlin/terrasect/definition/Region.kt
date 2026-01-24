package terrasect.definition

class Region(
    val name: String,
    val budget: Int,
    val children: Set<Region>,
    climate: ClimateSettings? = null,
    height: HeightConstraints? = null,
    noise: NoiseConstraints? = null,
    biomes: SelectionRules? = null,
    structures: SelectionRules? = null,
    mobs: SelectionRules? = null,
    strategy: Strategy? = null,
) :
    RegionDefinition(
        climate,
        height,
        noise,
        biomes,
        structures,
        mobs,
        strategy,
    ) {
  val hasChildren = children.isNotEmpty()

  companion object {
    fun empty(name: String): Region = Region(name, 10000, emptySet())
  }
}
