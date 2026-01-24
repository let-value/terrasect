package terrasect.definition

import kotlin.math.max
import kotlin.math.min

enum class StrategyId(val value: Byte) {
  HEX(1),
  VORONOI(2),
  SUBDIVISION(3),
  TEMPLATE(4),
}

interface Strategy {

  companion object {
    const val SEPARATOR: Char = ','

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

  class Hex(val ringRegionName: String? = null) : Strategy

  class Voronoi(relaxationIterations: Int = 5) : Strategy {
    val relaxationIterations = max(0, min(20, relaxationIterations))
  }

  class Subdivision(jitter: Float = 0.05f) : Strategy {
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
      Strategy
}
