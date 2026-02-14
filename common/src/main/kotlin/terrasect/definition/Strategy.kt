package terrasect.definition

import kotlin.math.max
import kotlin.math.min

enum class StrategyId(val value: Byte) {
  HEX(1),
  VORONOI(2),
  SUBDIVISION(3),
  TEMPLATE(4),
}

interface StrategySettings {
  fun build(definition: RegionDefinition, children: Set<Region>): Strategy
}

class VoronoiSettings(val relaxationIterations: Int) : Strategy {

  companion object {
    fun builder(relaxationIterations: Int = 5) = Builder(relaxationIterations)
  }

  class Builder(private var relaxationIterations: Int) : StrategySettings {

    fun relaxationIterations(relaxationIterations: Int) = apply {
      this.relaxationIterations = relaxationIterations
    }

    override fun build(definition: RegionDefinition, children: Set<Region>) =
      VoronoiSettings(max(0, min(20, relaxationIterations)))
  }
}

class SubdivisionSettings(val jitter: Float) : Strategy {

  companion object {
    fun builder(jitter: Float = 0.05f) = Builder(jitter)
  }

  class Builder(var jitter: Float) : StrategySettings {

    fun jitter(jitter: Float) = apply { this.jitter = jitter }

    override fun build(definition: RegionDefinition, children: Set<Region>) =
      SubdivisionSettings(max(0f, min(0.5f, jitter)))
  }
}

enum class TemplateType {
  AUTO,
  BINARY,
  TRIANGLE,
  CENTER_SURROUND,
  RADIAL,
}

class TemplateSettings(val type: TemplateType, centerRegionName: String? = null) : Strategy {
  companion object {
    fun builder(type: TemplateType = TemplateType.AUTO) = Builder(type)
  }

  class Builder(private var type: TemplateType) : StrategySettings {
    private var centerRegionName: String? = null

    fun type(type: TemplateType) = apply { this.type = type }

    fun centerRegionName(centerRegionName: String?) = apply {
      this.centerRegionName = centerRegionName
    }

    override fun build(definition: RegionDefinition, children: Set<Region>) =
      TemplateSettings(type, centerRegionName)
  }
}

interface Strategy {

  companion object {
    const val SEPARATOR: Char = ','

    fun voronoi() = VoronoiSettings.builder()

    fun voronoi(relaxationIterations: Int) = VoronoiSettings.builder(relaxationIterations)

    fun subdivision() = SubdivisionSettings.builder()

    fun subdivision(jitter: Float) = SubdivisionSettings.builder(jitter)

    fun template() = TemplateSettings.builder()

    fun template(type: TemplateType) = TemplateSettings.builder(type)

    fun centerSurround(centerRegionName: String) =
      TemplateSettings.builder(TemplateType.CENTER_SURROUND).centerRegionName(centerRegionName)
  }
}
