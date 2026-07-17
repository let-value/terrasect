package terrasect.definition

import terrasect.sdf.Decoration

class Region(
  val name: String,
  val budget: Long,
  val children: Set<Region>,
  val strategy: Strategy? = null,
  val decorations: List<Decoration> = emptyList(),
  val originAnchor: Boolean = false,
  val climate: ClimateConstraints? = null,
  val height: HeightConstraints? = null,
  val noise: NoiseConstraints? = null,
  val biomes: SelectionConstraints? = null,
  val structures: StructureConstraints? = null,
  val mobs: SelectionConstraints? = null,
  val loot: SelectionConstraints? = null,
) {
  val bytes = name.toByteArray()
  val hasChildren = children.isNotEmpty()

  companion object {
    fun empty(name: String): Region = Region(name, 10000, emptySet())
  }
}
