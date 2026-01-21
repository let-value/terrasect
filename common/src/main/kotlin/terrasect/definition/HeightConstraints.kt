package terrasect.definition

class HeightConstraints(val minY: Int, val maxY: Int? = null) {

  fun inheritParent(parent: HeightConstraints?): HeightConstraints {
    if (parent == null) return this
    return HeightConstraints(minY = this.minY, maxY = this.maxY ?: parent.maxY)
  }

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

    fun build(): HeightConstraints {
      return HeightConstraints(minY, maxY)
    }
  }
}
