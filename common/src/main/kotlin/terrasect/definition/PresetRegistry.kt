package terrasect.definition

import net.minecraft.world.level.levelgen.presets.WorldPresets
import terrasect.compat.ResourceKeyCompat
import terrasect.presets.CLIMATE_DEBUG
import terrasect.presets.Presets

object PresetRegistry {
  val NORMAL = ResourceKeyCompat.getKeyId(WorldPresets.NORMAL)
  var forcePresetId: String? = null
  val presets = mutableMapOf(Presets.CLIMATE_DEBUG.toString() to CLIMATE_DEBUG)

  fun preset(id: String) = presets.getOrPut(id) { RegionRegistry() }

  fun resolve(requestedPresetId: String?): RegionRegistry? {
    forcePresetId?.let {
      return presets[it]
    }

    requestedPresetId?.let {
      return presets[it]
    }

    return null
  }
}
