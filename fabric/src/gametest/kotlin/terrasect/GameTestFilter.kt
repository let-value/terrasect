package terrasect

import kotlin.reflect.KClass

/**
 * Utility for filtering which game tests to run via the `-Ptest=` Gradle property, which is
 * forwarded as the `test` JVM system property.
 *
 * Usage (run only one test):
 * ```
 * ./gradlew :fabric:runClientGameTest -Ptest=VanillaWorldDigestTest
 * ```
 *
 * Multiple tests (comma-separated):
 * ```
 * ./gradlew :fabric:runClientGameTest -Ptest=VanillaWorldDigestTest,TerrasectWorldDigestTest
 * ```
 *
 * No filter — runs all tests:
 * ```
 * ./gradlew :fabric:runClientGameTest
 * ```
 */
object GameTestFilter {
  val FOCUS: String? = null

  private val included: Set<String>? by lazy {
    (FOCUS ?: System.getProperty("test"))
        ?.takeIf { it.isNotBlank() }
        ?.split(",")
        ?.map { it.trim().lowercase() }
        ?.toSet()
  }

  /**
   * Returns `true` if this test should run. When no `-Ptest=` filter is set all tests run.
   * Otherwise only tests whose simple class name matches one of the comma-separated entries run
   * (case-insensitive).
   *
   * Typical call-site: `if (!GameTestFilter.shouldRun(this::class)) return`
   */
  fun shouldRun(klass: KClass<*>): Boolean {
    val name = klass.simpleName ?: return true
    return included == null || name.lowercase() in included!!
  }
}
