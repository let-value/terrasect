package terrasect.definition

import net.minecraft.world.level.levelgen.presets.WorldPresets
import terrasect.compat.ResourceKeyCompat

class PresetRegistry {

  var forcePresetId: String? = null
  val presets = mutableMapOf<String, RegionRegistry>()

  fun preset(id: String) = presets.getOrPut(id) { RegionRegistry() }

  fun resolve(requestedPresetId: String?): RegionRegistry {
    forcePresetId?.let {
      return this.preset(it)
    }
    requestedPresetId?.let {
      return this.preset(it)
    }
    return this.preset(NORMAL)
  }

  companion object {
    val NORMAL = ResourceKeyCompat.getKeyId(WorldPresets.NORMAL)
  }
}
