package terrasect.definition

class Region(
    val name: String,
    val budget: Double,
    val children: Set<Region>,
    val strategy: Strategy? = null,
    val climate: ClimateSettings? = null,
    val height: HeightConstraints? = null,
    val noise: NoiseConstraints? = null,
    val biomes: SelectionRules? = null,
    val structures: SelectionRules? = null,
    val mobs: SelectionRules? = null,
) {
  val hasChildren = children.isNotEmpty()

  companion object {
    fun empty(name: String): Region = Region(name, 10000.0, emptySet())
  }
}
