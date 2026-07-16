package terrasect.definition

import kotlin.math.PI
import kotlin.math.ceil

data class ForcedStructure(val name: String, val budget: Long?, val radius: Long? = null)

class StructureConstraints(
  val selection: SelectionConstraints?,
  val spacing: Int?,
  val separation: Int?,
  val frequency: Float?,
  val forced: List<ForcedStructure>,
) {
  companion object {
    fun builder(): Builder = Builder()
  }

  class Builder {
    private val selectionBuilder = SelectionConstraints.builder()
    private var hasSelection = false
    private var spacing: Int? = null
    private var separation: Int? = null
    private var frequency: Float? = null
    private val forced = mutableListOf<ForcedStructure>()

    fun allowMods(vararg mods: String) = apply {
      hasSelection = true
      selectionBuilder.allowMods(*mods)
    }

    fun allowTags(vararg tags: String) = apply {
      hasSelection = true
      selectionBuilder.allowTags(*tags)
    }

    fun allowNames(vararg names: String) = apply {
      hasSelection = true
      selectionBuilder.allowNames(*names)
    }

    fun blockMods(vararg mods: String) = apply {
      hasSelection = true
      selectionBuilder.blockMods(*mods)
    }

    fun blockTags(vararg tags: String) = apply {
      hasSelection = true
      selectionBuilder.blockTags(*tags)
    }

    fun blockNames(vararg names: String) = apply {
      hasSelection = true
      selectionBuilder.blockNames(*names)
    }

    fun spacing(spacing: Int) = apply { this.spacing = spacing }

    fun separation(separation: Int) = apply { this.separation = separation }

    fun frequency(frequency: Float) = apply { this.frequency = frequency }

    fun force(name: String, budget: Long? = null) = apply {
      forced.add(ForcedStructure(name, budget))
    }

    fun forceRadius(name: String, radius: Long) = apply {
      forced.add(ForcedStructure(name, ceil(PI * radius * radius).toLong(), radius))
    }

    // Same-region duplication (RegionBuilder.copy) keeps forced entries; parent→child
    // inheritance must not, otherwise every child cell would spawn its own copy.
    fun copyForced(source: Builder) = apply { forced.addAll(source.forced) }

    fun inheritParent(parent: Builder) = apply {
      if (parent.hasSelection) {
        hasSelection = true
        selectionBuilder.inheritParent(parent.selectionBuilder)
      }
      if (spacing == null) spacing = parent.spacing
      if (separation == null) separation = parent.separation
      if (frequency == null) frequency = parent.frequency
    }

    fun build(): StructureConstraints =
      StructureConstraints(
        selection = if (hasSelection) selectionBuilder.build() else null,
        spacing = spacing,
        separation = separation,
        frequency = frequency,
        forced = forced.toList(),
      )
  }
}
