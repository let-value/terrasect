// Single source of truth for the pinned runtime tools. Bump the version/url/sha256 here only.
// Every download task derives its URL + checksum from this table.

/**
 * Pinned release tools for the runtime-test infrastructure.
 *
 * The checksums were captured from the releases on 2026-08-27 and verified against the download
 * bytes. Keep exactly one place to bump a tool — the download/verify tasks below read every value
 * from this object.
 */
object RuntimeTestPins {

  /** HeadlessMC — headless Minecraft launcher + version-specific mod installer. */
  const val HMC_VERSION = "2.10.0"
  const val HMC_JAR_URL =
    "https://github.com/headlesshq/headlessmc/releases/download/2.10.0/" + "headlessmc-launcher-2.10.0.jar"
  const val HMC_JAR_SHA256 = "52bd5006f478377b3893011d458562977d38c65ead6d2b31089beb4d614f13cd"

  /** Ferium — multi-source mod resolver (Modrinth + Forge/Modrinth modpacks). */
  const val FERIUM_VERSION = "4.7.1"
  const val FERIUM_LINUX_URL =
    "https://github.com/gorilla-devs/ferium/releases/download/v4.7.1/" + "ferium-linux-nogui.zip"
  const val FERIUM_LINUX_SHA256 = "8d4a357c6eaf05bc7804d1916fe597b58f10d57fe16443b9b767776e99049d14"
  const val FERIUM_MACOS_URL =
    "https://github.com/gorilla-devs/ferium/releases/download/v4.7.1/" + "ferium-macos-nogui.zip"
  const val FERIUM_MACOS_SHA256 = "5f5350f81763195b6d28deb6f67c4d971ba4d3cac18a133d9568def9fba199d3"

  /**
   * The Minecraft version the runtime-test matrix covers. Pinned centrally so the manifest
   * validation and the CI smoke matrix agree on the exact same set.
   */
  const val MOD_VERSION_PROP = "0.2.3"
  const val LATEST_MC = "26.2"

  /**
   * The full supported MC + loader matrix, keyed by Gradle segment id (e.g. `26.2.x`) so it stays
   * in lockstep with `settings.gradle.kts` and the CI smoke matrix. Values are Minecraft versions.
   */
  val SUPPORTED_MATRIX: Map<String, Set<String>> =
    mapOf(
      "1.20.1" to setOf("fabric"),
      "1.21.1" to setOf("fabric", "neoforge"),
      "1.21.11" to setOf("fabric", "neoforge"),
      "26.1.x" to setOf("fabric", "neoforge"),
      "26.2.x" to setOf("fabric", "neoforge"),
    )

  /** All unique (loader, version) pairs covered by [SUPPORTED_MATRIX]. */
  val SUPPORTED_LOADERS: Set<String>
    get() = SUPPORTED_MATRIX.values.flatMapTo(mutableSetOf()) { it }

  /** Supported Minecraft versions, ordered oldest-first. */
  val SUPPORTED_MC_VERSIONS: List<String>
    get() = SUPPORTED_MATRIX.entries.map { it.key }.toList()

  /** Build a Gradle segment id (`.x`) from a Minecraft version (e.g. `26.2` -> `26.2.x`). */
  fun gradleSegment(mcVersion: String): String = "${mcVersion}.x"

  /** The Minecraft version for a Gradle segment id (`26.2.x` -> `26.2`). */
  fun mcVersionOf(segment: String): String = segment.removeSuffix(".x")

  /** Whether this machine is macOS — used to pick the matching Ferium release. */
  val isMac: Boolean = System.getProperty("os.name").lowercase().contains("mac")

  fun feriumUrl(): String = if (isMac) FERIUM_MACOS_URL else FERIUM_LINUX_URL

  fun feriumSha256(): String = if (isMac) FERIUM_MACOS_SHA256 else FERIUM_LINUX_SHA256

  fun feriumPlatform(): String = if (isMac) "macos" else "linux"

  /**
   * Success-marker substring asserted after a HeadlessMC launch. Tuned per loader on the first real
   * run; Terrasect logs this string on init so the assertion is stronger than "process exited".
   */
  fun successMarkerFor(loader: String): String = "Terrasect"

  /** Map a plain Minecraft version to the Stonecutter segment id used as a matrix key. */
  fun matrixKey(mc: String): String {
    val plain = mc.removeSuffix(".x")
    return if (SUPPORTED_MATRIX.containsKey(plain)) plain else "${plain}.x"
  }
}
