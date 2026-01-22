package terrasect.compat

import net.minecraft.resources.ResourceKey

object ResourceKeyCompat {
  fun getKeyId(key: ResourceKey<*>): String {
    return key.identifier().toString()
  }
}
