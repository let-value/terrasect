package terrasect.definition

class ClimateRange(val min: Long, val max: Long) {
  companion object {
    fun range(min: Long, max: Long): ClimateRange {
      val lo = kotlin.math.min(min, max)
      val hi = kotlin.math.max(min, max)
      return ClimateRange(lo, hi)
    }

    fun exact(value: Long) = ClimateRange(value, value)
  }

  fun hasVariation() = min != max

  fun center() = (min + max) * 0.5f

  fun size() = max - min
}

data class ClimateSettings(
    val temperature: ClimateRange? = null,
    val humidity: ClimateRange? = null,
    val continentalness: ClimateRange? = null,
    val erosion: ClimateRange? = null,
    val depth: ClimateRange? = null,
    val weirdness: ClimateRange? = null,
    val precipitation: String? = null,
    val climatePreset: String? = null,
) {

  companion object {
    fun builder(): Builder = Builder()
  }

  class Builder {
    private var temperature: ClimateRange? = null
    private var humidity: ClimateRange? = null
    private var continentalness: ClimateRange? = null
    private var erosion: ClimateRange? = null
    private var depth: ClimateRange? = null
    private var weirdness: ClimateRange? = null
    private var precipitation: String? = null
    private var climatePreset: String? = null

    fun temperature(min: Long, max: Long) = apply {
      this.temperature = ClimateRange.range(min, max)
    }

    fun temperature(value: Long) = apply { this.temperature = ClimateRange.exact(value) }

    fun humidity(min: Long, max: Long) = apply { this.humidity = ClimateRange.range(min, max) }

    fun humidity(value: Long) = apply { this.humidity = ClimateRange.exact(value) }

    fun continentalness(min: Long, max: Long) = apply {
      this.continentalness = ClimateRange.range(min, max)
    }

    fun continentalness(value: Long) = apply { this.continentalness = ClimateRange.exact(value) }

    fun erosion(min: Long, max: Long) = apply { this.erosion = ClimateRange.range(min, max) }

    fun erosion(value: Long) = apply { this.erosion = ClimateRange.exact(value) }

    fun depth(min: Long, max: Long) = apply { this.depth = ClimateRange.range(min, max) }

    fun depth(value: Long) = apply { this.depth = ClimateRange.exact(value) }

    fun weirdness(min: Long, max: Long) = apply { this.weirdness = ClimateRange.range(min, max) }

    fun weirdness(value: Long) = apply { this.weirdness = ClimateRange.exact(value) }

    fun precipitation(precipitation: String) = apply { this.precipitation = precipitation }

    fun climatePreset(climatePreset: String) = apply { this.climatePreset = climatePreset }

    fun inheritParent(parent: Builder) = apply {
      this.temperature = this.temperature ?: parent.temperature
      this.humidity = this.humidity ?: parent.humidity
      this.continentalness = this.continentalness ?: parent.continentalness
      this.erosion = this.erosion ?: parent.erosion
      this.depth = this.depth ?: parent.depth
      this.weirdness = this.weirdness ?: parent.weirdness
      this.precipitation = this.precipitation ?: parent.precipitation
      this.climatePreset = this.climatePreset ?: parent.climatePreset
    }

    fun build(): ClimateSettings =
        ClimateSettings(
            temperature = temperature,
            humidity = humidity,
            continentalness = continentalness,
            erosion = erosion,
            depth = depth,
            weirdness = weirdness,
            precipitation = precipitation,
            climatePreset = climatePreset,
        )
  }
}
