package terrasect.definition

import terrasect.helpers.NoiseTransform

class NoiseConstraints(
    val noises: Map<String, NoiseTransform>,
    val densityFunctions: Map<String, NoiseTransform>,
) {
  companion object {
    fun builder() = Builder()
  }

  class Builder {
    private val noises = mutableMapOf<String, NoiseTransform>()
    private val densityFunctions = mutableMapOf<String, NoiseTransform>()

    fun noise(name: String, transform: NoiseTransform) = apply { noises[name] = transform }

    fun noise(name: String, consumer: (NoiseTransform.Builder) -> Unit) = apply {
      val transform = NoiseTransform.builder().apply(consumer).build()
      noises[name] = transform
    }

    fun densityFunction(name: String, transform: NoiseTransform) = apply {
      densityFunctions[name] = transform
    }

    fun inheritParent(parent: Builder) = apply {
      for ((name, transform) in parent.noises) {
        this.noises.putIfAbsent(name, transform)
      }
      for ((name, transform) in parent.densityFunctions) {
        this.densityFunctions.putIfAbsent(name, transform)
      }
    }

    fun build(): NoiseConstraints {
      return NoiseConstraints(noises, densityFunctions)
    }
  }
}
