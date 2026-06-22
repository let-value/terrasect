package terrasect.compat

import net.minecraft.resources.ResourceKey

object ResourceKeyCompat {
  @Suppress("NOTHING_TO_INLINE")
  inline fun getKeyId(key: ResourceKey<*>): String {
    // spotless:off
    //? if >=1.21.11 {
    return key.identifier().toString()
    //?} else {
    /*return key.location().toString()
     */
    //?}
    // spotless:on
  }

  @Suppress("NOTHING_TO_INLINE")
  inline fun getKeyPath(key: ResourceKey<*>): String {
    // spotless:off
    //? if >=1.21.11 {
    return key.identifier().path
    //?} else {
    /*return key.location().path
     */
    //?}
    // spotless:on
  }
}
