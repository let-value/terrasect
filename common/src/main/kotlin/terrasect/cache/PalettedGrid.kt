package terrasect.cache

class PalettedGrid<T>(
    val width: Int = 16,
    val height: Int = 16,
    val originX: Int = 0,
    val originZ: Int = 0,
) {

  /** Palette of unique values — typically very small (a handful of regions per chunk). */
  val palette = ArrayList<T>()

  /** Per-cell palette index. -1 means no value assigned. */
  val cells = IntArray(width * height) { -1 }

  fun idx(x: Int, z: Int): Int {
    val localX = x - originX
    val localZ = z - originZ
    return localX * height + localZ
  }

  /**
   * Returns the palette index for [value], adding it to the palette if it isn't already present.
   * Uses identity comparison (===) since Region instances are shared singletons from a registry.
   */
  private fun paletteIndex(value: T): Int {
    for (i in palette.indices) {
      if (palette[i] === value) return i
    }
    val i = palette.size
    palette.add(value)
    return i
  }

  fun add(x: Int, z: Int, value: T) {
    val cell = idx(x, z)
    cells[cell] = paletteIndex(value)
  }

  fun get(x: Int, z: Int): T? {
    val cell = idx(x, z)
    val pi = cells[cell]
    if (pi == -1) {
      return null
    }
    return palette[pi]
  }
}
