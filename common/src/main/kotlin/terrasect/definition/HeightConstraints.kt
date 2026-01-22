package terrasect.definition

class HeightConstraints(val minY: Int, val maxY: Int? = null) {

  companion object {
    fun builder(): Builder = Builder()
  }

  class Builder {
    private var minY: Int = 0
    private var maxY: Int? = null

    fun range(minY: Int, maxY: Int) = apply {
      this.minY = minY
      this.maxY = maxY
    }

    fun exact(minY: Int) = apply {
      this.minY = minY
      this.maxY = null
    }

    fun inheritParent(parent: Builder) = apply {
      this.minY = this.minY.takeIf { it != 0 } ?: parent.minY
      this.maxY = this.maxY ?: parent.maxY
    }

    fun build(): HeightConstraints {
      return HeightConstraints(minY, maxY)
    }
  }
}
