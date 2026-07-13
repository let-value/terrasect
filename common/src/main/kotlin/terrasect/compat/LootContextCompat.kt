package terrasect.compat

import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3

object LootContextCompat {
  fun getOrigin(context: LootContext): Vec3? {
    // spotless:off
    //? if >=1.21.11 {
    return context.getOptionalParameter(LootContextParams.ORIGIN)
    //?} else {
    /*return context.getParamOrNull(LootContextParams.ORIGIN)
     */
    //?}
    // spotless:on
  }
}
