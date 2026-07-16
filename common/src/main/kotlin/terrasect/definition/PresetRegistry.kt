package terrasect.definition

import terrasect.presets.CLIMATE_DEBUG
import terrasect.presets.Presets

object PresetRegistry {
  var forcePresetId: String? = null
  var configuredPresetId: String? = null
  val presets =
    mutableMapOf(
      Presets.CLIMATE_DEBUG.toString() to CLIMATE_DEBUG,
      Presets.CLIMATE_DEBUG.id to CLIMATE_DEBUG,
    )

  fun preset(id: String) = presets.getOrPut(id) { RegionRegistry() }

  fun resolve(requestedPresetId: String?): RegionRegistry? {
    forcePresetId?.let {
      return presets[it]
    }

    configuredPresetId?.let {
      return presets[it]
    }

    requestedPresetId?.let {
      return presets[it]
    }

    return null
  }
}
