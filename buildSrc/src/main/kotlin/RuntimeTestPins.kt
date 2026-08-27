// Single source of truth for the pinned runtime tools. Bump the version/url/sha256 here only.
// Every download task derives its URL + checksum from this table.

/**
 * Pinned release tools for the runtime-test infrastructure.
 *
 * All four release URLs and SHA-256 values were downloaded from the authoritative GitHub release
 * assets and byte-for-byte verified on 2026-08-27:
 * - HMC
 *   https://github.com/headlesshq/headlessmc/releases/download/2.10.0/headlessmc-launcher-2.10.0.jar
 * - Ferium linux-nogui, macos-arm, macos-x64 at
 *   https://github.com/gorilla-devs/ferium/releases/download/v4.7.1/ The download/verify tasks
 *   below read every value from this object. Keep exactly one place to bump a tool.
 */
object RuntimeTestPins {

  /** HeadlessMC — headless Minecraft launcher + version-specific mod installer. */
  const val HMC_VERSION = "2.10.0"
  const val HMC_JAR_URL =
    "https://github.com/headlesshq/headlessmc/releases/download/2.10.0/" +
      "headlessmc-launcher-2.10.0.jar"
  const val HMC_JAR_SHA256 = "52bd5006f478377b3893011d458562977d38c65ead6d2b31089beb4d614f13cd"

  /**
   * Ferium — multi-source mod resolver (Modrinth + Forge/Modrinth modpacks).
   *
   * The `v4.7.1` release ships macOS assets **per-arch** (`ferium-macos-arm.zip` /
   * `ferium-macos-x64.zip`); there is no single `ferium-macos-nogui.zip` any more, so the macOS
   * URL/sha256 are chosen from the machine architecture at run time. All URLs and shas below were
   * checked against the authoritative GitHub release assets on 2026-08-27.
   */
  const val FERIUM_VERSION = "4.7.1"

  const val FERIUM_LINUX_URL =
    "https://github.com/gorilla-devs/ferium/releases/download/v4.7.1/" + "ferium-linux-nogui.zip"
  const val FERIUM_LINUX_SHA256 = "8d4a357c6eaf05bc7804d1916fe597b58f10d57fe16443b9b767776e99049d14"

  const val FERIUM_MACOS_ARM_URL =
    "https://github.com/gorilla-devs/ferium/releases/download/v4.7.1/" + "ferium-macos-arm.zip"
  const val FERIUM_MACOS_ARM_SHA256 =
    "5f5350f81763195b6d28deb6f67c4d971ba4d3cac18a133d9568def9fba199d3"

  const val FERIUM_MACOS_X64_URL =
    "https://github.com/gorilla-devs/ferium/releases/download/v4.7.1/" + "ferium-macos-x64.zip"
  const val FERIUM_MACOS_X64_SHA256 =
    "d307fefd688dca58383a749a19d0b99e5890f7681331166b26936b59e855dcce"

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

  /** Whether this machine is Apple Silicon (as opposed to Intel/x64 on macOS). */
  val isMacArm: Boolean = isMac && System.getProperty("os.arch").lowercase().contains("arm")

  fun feriumUrl(): String =
    if (isMac) (if (isMacArm) FERIUM_MACOS_ARM_URL else FERIUM_MACOS_X64_URL) else FERIUM_LINUX_URL

  fun feriumSha256(): String =
    if (isMac) (if (isMacArm) FERIUM_MACOS_ARM_SHA256 else FERIUM_MACOS_X64_SHA256)
    else FERIUM_LINUX_SHA256

  /** Which release filename the URL below resolves to, for tests and diagnostics. */
  fun feriumPlatform(): String =
    if (isMac) (if (isMacArm) "macos-arm" else "macos-x64") else "linux"

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
