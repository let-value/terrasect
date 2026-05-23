package terrasect.definition

import terrasect.helpers.NoiseTransform

class NoiseConstraints(
  val noises: Map<String, NoiseTransform>,
  val densityFunctions: Map<String, NoiseTransform>,
  val blendWidth: Float = DEFAULT_BLEND_WIDTH,
) {
  companion object {
    const val DEFAULT_BLEND_WIDTH: Float = 32f

    fun builder() = Builder()
  }

  fun hasAnyConstraints(): Boolean = noises.isNotEmpty() || densityFunctions.isNotEmpty()

  class Builder {
    private val noises = mutableMapOf<String, NoiseTransform>()
    private val densityFunctions = mutableMapOf<String, NoiseTransform>()
    private var blendWidth: Float = DEFAULT_BLEND_WIDTH
    private var blendWidthExplicit: Boolean = false

    fun noise(name: String, transform: NoiseTransform) = apply { noises[name] = transform }

    fun noise(name: String, consumer: (NoiseTransform.Builder) -> Unit) = apply {
      val transform = NoiseTransform.builder().apply(consumer).build()
      noises[name] = transform
    }

    fun densityFunction(name: String, transform: NoiseTransform) = apply {
      densityFunctions[name] = transform
    }

    fun densityFunction(name: String, consumer: (NoiseTransform.Builder) -> Unit) = apply {
      val transform = NoiseTransform.builder().apply(consumer).build()
      densityFunctions[name] = transform
    }

    fun blendWidth(width: Float) = apply {
      this.blendWidth = width
      this.blendWidthExplicit = true
    }

    fun inheritParent(parent: Builder) = apply {
      for ((name, transform) in parent.noises) {
        this.noises.putIfAbsent(name, transform)
      }
      for ((name, transform) in parent.densityFunctions) {
        this.densityFunctions.putIfAbsent(name, transform)
      }
      if (!blendWidthExplicit) {
        this.blendWidth = parent.blendWidth
      }
    }

    fun build(): NoiseConstraints {
      return NoiseConstraints(noises.toMap(), densityFunctions.toMap(), blendWidth)
    }
  }
}
