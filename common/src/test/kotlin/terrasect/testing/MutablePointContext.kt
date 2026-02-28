package terrasect.testing

import net.minecraft.world.level.levelgen.DensityFunction

class MutablePointContext : DensityFunction.FunctionContext {
  private var x = 0
  private var y = 0
  private var z = 0

  fun set(x: Int, y: Int, z: Int) {
    this.x = x
    this.y = y
    this.z = z
  }

  override fun blockX() = x
  override fun blockY() = y
  override fun blockZ() = z
}
