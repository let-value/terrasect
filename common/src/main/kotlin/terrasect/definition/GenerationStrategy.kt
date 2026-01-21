package terrasect.definition

import kotlin.math.max
import kotlin.math.min

interface GenerationStrategy {

  companion object {
    fun hex() = Hex()

    fun hex(ringRegionName: String) = Hex(ringRegionName)

    fun voronoi() = Voronoi()

    fun voronoi(relaxationIterations: Int) = Voronoi(relaxationIterations)

    fun subdivision() = Subdivision()

    fun subdivision(jitter: Float) = Subdivision(jitter)

    fun template() = Template()

    fun template(type: TemplateType) = Template(type)

    fun centerSurround(centerRegionName: String) =
        Template(TemplateType.CENTER_SURROUND, centerRegionName)
  }

  class Hex(val ringRegionName: String? = null) : GenerationStrategy

  class Voronoi(relaxationIterations: Int = 5) : GenerationStrategy {
    val relaxationIterations = max(0, min(20, relaxationIterations))
  }

  class Subdivision(jitter: Float = 0.05f) : GenerationStrategy {
    val jitter = max(0f, min(0.5f, jitter))
  }

  enum class TemplateType {
    AUTO,
    BINARY,
    TRIANGLE,
    CENTER_SURROUND,
    RADIAL,
  }

  class Template(val type: TemplateType = TemplateType.AUTO, centerRegionName: String? = null) :
      GenerationStrategy
}
