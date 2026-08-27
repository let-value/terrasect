// Declarative, reviewable runtime-test descriptors.
//
// These files drive the release-form runtime testing. They live under runtime-tests/scenarios/ in
// plain YAML and are parsed with the minimal self-contained reader in YamlReader.kt, so there is
// no third-party library on the buildSrc classpath (keeping ./gradlew configuration offline).
//
// The matrix of supported (loader, mcVersion) pairs is fixed in RuntimeTestPins.SUPPORTED_MATRIX so
// a missing fixture for any lane is a hard validation error rather than a silent skip.

import java.io.File

/** Where a released artifact was resolved from. */
enum class Platform { MODRINTH, CURSEFORGE }

/** A third-party mod resolved by Ferium for a compat scenario. */
data class ExternalMod(
  val name: String,
  val platform: Platform,
  val project: String,
  val version: String?,
)

/** Which lifecycle a scenario represents. */
enum class Scenario { BUILD, PUBLISHED, COMPAT }

/** A single runtime-test lane (one MC version + one loader). */
data class RuntimeScenario(
  val mc: String,
  val loader: String,
  val scenario: Scenario,
  val source: Platform?,
  val project: String?,
  val gameVersions: List<String>,
  val dependencies: List<String>,
  val externalMods: List<ExternalMod>,
  val note: String?,
)

/** Top-level descriptor manifest. */
data class RuntimeManifest(
  val modId: String,
  val latest: String,
  val modVersion: String,
  val scenarios: List<RuntimeScenario>,
)

/** Errors detected while validating descriptors, collected (never thrown) so a whole run can report. */
class ValidationError(message: String) : Exception(message)

/**
 * Parses and validates the runtime-test descriptors. Parses are lenient about ordering; validation
 * enforces that every supported lane is covered by every scenario that must cover all lanes.
 */
object RuntimeTestDescriptors {

  fun parse(file: File): RuntimeManifest {
    val root = YamlReader.parse(file)
    val modId = root["mod_id"]?.requireString() ?: throw ValidationError("missing mod_id in ${file.name}")
    val latest = root["latest"]?.requireString() ?: throw ValidationError("missing latest in ${file.name}")
    val modVersion = root["mod_version"]?.requireString() ?: throw ValidationError("missing mod_version in ${file.name}")
    val listNode = root["scenarios"] ?: throw ValidationError("missing scenarios list in ${file.name}")

    val scenarios =
      when (listNode) {
        is YamlNode.Sequence -> listNode.items.mapIndexed { i, item -> parseScenario(item, file, i) }
        else -> throw ValidationError("scenarios must be a list in ${file.name}")
      }
    return RuntimeManifest(modId, latest, modVersion, scenarios)
  }

  fun parse(file: File, expected: RuntimeManifest): Boolean {
    val actual = parse(file)
    if (actual.modId != expected.modId || actual.latest != expected.latest || actual.modVersion != expected.modVersion) {
      throw ValidationError("${file.name}: manifest header does not match expected")
    }
    return true
  }

  private fun parseScenario(item: YamlNode, file: File, index: Int): RuntimeScenario {
    val mapping =
      item as? YamlNode.Mapping ?: throw ValidationError("scenario #$index in ${file.name} must be a mapping")
    val mc = mapping["mc"]?.requireString() ?: throw ValidationError("scenario #$index missing mc")
    val loader = mapping["loader"]?.requireString() ?: throw ValidationError("scenario #$index missing loader")
    val scenarioStr = mapping["scenario"]?.requireString() ?: throw ValidationError("scenario #$index missing scenario")
    val scenario =
      Scenario.entries.firstOrNull { it.name.equals(scenarioStr, ignoreCase = true) }
        ?: throw ValidationError("scenario #$index has unknown scenario '$scenarioStr'")

    val source =
      mapping["source"]?.requireString()?.let { Platform.entries.firstOrNull { p -> p.name.equals(it, ignoreCase = true) } }
    val project = mapping["project"]?.requireString()
    val gameVersions = parseStringList(mapping["gameVersions"], "scenario #$index gameVersions")
    val dependencies = parseStringList(mapping["dependencies"], "scenario #$index dependencies")

    val externalMods =
      (mapping["externalMods"] as? YamlNode.Sequence)?.items?.mapIndexed { j, e ->
          val em = e as? YamlNode.Mapping ?: throw ValidationError("externalMods#$j in ${file.name} must be a mapping")
          ExternalMod(
            em["name"]?.requireString() ?: throw ValidationError("externalMods#$j missing name"),
            (em["platform"]?.requireString()?.let {
              Platform.entries.firstOrNull { p -> p.name.equals(it, ignoreCase = true) }
            }) ?: throw ValidationError("externalMods#$j missing platform"),
            em["project"]?.requireString() ?: throw ValidationError("externalMods#$j missing project"),
            em["version"]?.requireString(),
          )
        }
        ?: emptyList()
    val note = mapping["note"]?.requireString()

    return RuntimeScenario(mc, loader, scenario, source, project, gameVersions, dependencies, externalMods, note)
  }

  private fun parseStringList(node: YamlNode?, field: String): List<String> {
    val seq = node as? YamlNode.Sequence ?: return emptyList()
    return seq.items.map { (it as? YamlNode.Scalar)?.value ?: throw ValidationError("$field item is not a scalar") }
  }

  /**
   * Validates a set of manifests. Throws a [ValidationError] (aggregating messages) if any supported
   * lane is missing from a scenario that must cover every lane.
   */
  fun validate(manifests: List<RuntimeManifest>) {
    val errors = mutableListOf<String>()

    // Validate the header for every manifest.
    val expectedHeader = manifests.firstOrNull()?.let { Triple(it.modId, it.latest, it.modVersion) }
    manifests.forEach { m ->
      if (expectedHeader != null && Triple(m.modId, m.latest, m.modVersion) != expectedHeader) {
        errors.add("${m.modId}/${m.latest}/${m.modVersion} mismatch")
      }
    }

    // Build coverage sets per scenario type.
    val buildPairs = coveragePairs(manifests, Scenario.BUILD)
    val modrinthPairs = coveragePairs(manifests, Scenario.PUBLISHED, Platform.MODRINTH)
    val curseforgePairs = coveragePairs(manifests, Scenario.PUBLISHED, Platform.CURSEFORGE)
    val compatPairs = coveragePairs(manifests, Scenario.COMPAT)

    RuntimeTestPins.SUPPORTED_MATRIX.forEach { (seg, loaders) ->
      val mc = RuntimeTestPins.mcVersionOf(seg)
      loaders.forEach { loader ->
        val pair = Pair(loader, mc)
        if (!buildPairs.contains(pair)) errors.add("BUILD lane missing for $loader $mc ($seg)")
        // Published coverage is split across Modrinth/CurseForge CI jobs, so a lane needs at
        // least one platform here; both must be exercised across the fixtures collectively.
        val anyPublished = modrinthPairs.contains(pair) || curseforgePairs.contains(pair)
        if (!anyPublished) errors.add("PUBLISHED lane missing for $loader $mc ($seg)")
        if (!compatPairs.contains(pair)) errors.add("COMPAT lane missing for $loader $mc ($seg)")
      }
    }

    // Every scenario lane must be a supported lane.
    manifests.forEach { m ->
      m.scenarios.forEach { s ->
        val seg = RuntimeTestPins.matrixKey(s.mc)
        val supportedLoaders = RuntimeTestPins.SUPPORTED_MATRIX[seg]
        val ok =
          supportedLoaders != null && (s.loader in supportedLoaders)
        if (!ok) errors.add("Unsupported lane ${s.loader} ${s.mc} (${seg}) in ${m.modId}")
        // External mods are only meaningful (and only validated) on COMPAT scenarios.
        if (s.externalMods.isNotEmpty() && s.scenario != Scenario.COMPAT) {
          errors.add("Non-compat lane ${s.loader} ${s.mc} (${seg}) references unknown mods: ${s.externalMods}")
        }
        if (s.scenario == Scenario.COMPAT && s.note == null) {
          errors.add("COMPAT lane ${s.loader} ${s.mc} (${seg}) missing explanatory note")
        }
      }
    }

    if (errors.isNotEmpty()) throw ValidationError(errors.joinToString("\n"))
  }

  private fun coveragePairs(manifests: List<RuntimeManifest>, scenario: Scenario, platform: Platform? = null): Set<Pair<String, String>> {
    val pairs = mutableSetOf<Pair<String, String>>()
    manifests.forEach { m ->
      m.scenarios.forEach { s ->
        if (s.scenario == scenario && (platform == null || s.source == platform)) {
          pairs.add(Pair(s.loader, s.mc))
        }
      }
    }
    return pairs
  }

  /** Loads and validates every manifest file in [dir]. */
  fun loadAll(dir: File): List<RuntimeManifest> {
    val found = dir.walkTopDown().filter { it.isFile && (it.extension == "yaml" || it.extension == "yml") }.toList()
    return found.map { parse(it) }.also { validate(it) }
  }
}
