package terrasect.config

import java.nio.file.Files
import java.nio.file.Path
import org.slf4j.LoggerFactory
import terrasect.Constants
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry
import terrasect.instrumentation.MetricsConfig

data class LoadedTerrasectConfig(
  val config: TerrasectConfig,
  val presets: Map<String, RegionRegistry>,
  val createdFiles: List<Path>,
)

private data class ParsedPreset(val registry: RegionRegistry, val warnings: List<String>)

object TerrasectConfigManager {
  private val log = LoggerFactory.getLogger("${Constants.MOD_ID}.config")

  fun initialize(configRoot: Path): LoadedTerrasectConfig {
    val directory = configRoot.resolve(Constants.MOD_ID)
    val createdFiles = DefaultConfigFiles.ensurePresent(directory)
    val config = parseConfig(directory.resolve(DefaultConfigFiles.CONFIG_FILE))

    val parsedPresets =
      DefaultConfigFiles.presetFiles(directory).associate { path ->
        val name = path.fileName.toString().removeSuffix(".toml")
        name to parsePreset(path)
      }
    val presets = parsedPresets.mapValues { it.value.registry }
    val knownPresetIds = PresetRegistry.presets.keys + presets.keys
    if (config.preset != null && config.preset !in knownPresetIds) {
      throw TerrasectConfigException(
        "${directory.resolve(DefaultConfigFiles.CONFIG_FILE)}: preset '${config.preset}' was not found"
      )
    }

    PresetRegistry.presets.putAll(presets)
    PresetRegistry.configuredPresetId = config.preset
    ConfigLogging.registryDebug = config.logging.registryDebug
    applyInstrumentation(config.instrumentation)
    if (config.logging.validationWarnings && config.preset != null) {
      parsedPresets[config.preset]?.warnings?.forEach(log::warn)
    }
    if (config.logging.loadSummary) {
      log.info(
        "Loaded {} Terrasect preset(s) from {}; active preset={}",
        presets.size,
        directory,
        config.preset ?: "none",
      )
      if (createdFiles.isNotEmpty()) {
        log.info("Created default Terrasect configuration: {}", createdFiles.joinToString())
      }
    }
    return LoadedTerrasectConfig(config, presets, createdFiles)
  }

  private fun parseConfig(path: Path): TerrasectConfig =
    try {
      TerrasectToml.parseConfig(Files.readString(path), path.toString())
    } catch (error: TerrasectConfigException) {
      throw error
    } catch (error: Exception) {
      throw TerrasectConfigException("$path: failed to read configuration", error)
    }

  private fun parsePreset(path: Path): ParsedPreset =
    try {
      val warnings = mutableListOf<String>()
      val registry =
        TerrasectToml.parsePreset(Files.readString(path), path.toString(), warnings::add)
      ParsedPreset(registry, warnings)
    } catch (error: TerrasectConfigException) {
      throw error
    } catch (error: Exception) {
      throw TerrasectConfigException("$path: failed to read preset", error)
    }

  private fun applyInstrumentation(config: InstrumentationConfig) {
    MetricsConfig.enabled = config.enabled
    MetricsConfig.countersEnabled = config.counters
    MetricsConfig.timersEnabled = config.timers
    MetricsConfig.clearScopeOverrides()
    for ((scope, options) in config.scopes) {
      MetricsConfig.setScopeEnabled(scope, options.enabled)
      MetricsConfig.setScopeCountersEnabled(scope, options.counters)
      MetricsConfig.setScopeTimersEnabled(scope, options.timers)
    }
    for ((event, enabled) in config.events) {
      MetricsConfig.setEventCountersEnabled(event, enabled)
    }
  }
}
