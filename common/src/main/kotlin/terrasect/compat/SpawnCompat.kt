package terrasect.compat

import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.biome.MobSpawnSettings

object SpawnCompat {
  @Suppress("NOTHING_TO_INLINE")
  inline fun getType(data: MobSpawnSettings.SpawnerData): EntityType<*> {
    // spotless:off
    //? if >=1.21.11 {
    return data.type()
    //?} else {
    /*return data.type
     */
    //?}
    // spotless:on
  }
}
