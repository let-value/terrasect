package terrasect.compat

import net.minecraft.resources.ResourceKey

object ResourceKeyCompat {
  @Suppress("NOTHING_TO_INLINE")
  inline fun getKeyId(key: ResourceKey<*>): String {
    return key.identifier().toString()
  }
}
