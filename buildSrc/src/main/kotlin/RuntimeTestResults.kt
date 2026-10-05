import org.gradle.api.GradleException

internal fun expectedGameTests(
  completionMarkers: Map<String, String>,
  testFilter: String,
): Map<String, String> {
  val selected = testFilter.split(',').map(String::trim).filter(String::isNotEmpty).toSet()
  return if (selected.isEmpty()) completionMarkers
  else completionMarkers.filterKeys(selected::contains)
}

internal fun matchedGameTests(
  logText: String,
  completionMarkers: Map<String, String>,
  testFilter: String,
): List<String> =
  expectedGameTests(completionMarkers, testFilter)
    .filterValues { logText.contains(it) }
    .keys
    .toList()

internal fun runtimeFailure(logText: String): String? {
  val failure =
    listOf(
        "Client gametests failed with an exception",
        "This crash report has been saved",
        "ModResolutionException",
        "Missing or unsupported mandatory dependencies",
        "NoClassDefFoundError",
        "Uncaught exception",
        "Client gametests stalled",
        "Server smoke failed",
        "Game crashed! Crash report saved to:",
      )
      .firstOrNull { logText.contains(it, ignoreCase = true) }
  if (failure != null) return "Runtime log contains '$failure'"
  val exitCodes =
    Regex("Minecraft exited with code: (-?\\d+)")
      .findAll(logText)
      .map { it.groupValues[1].toInt() }
      .toList()
  return if (exitCodes.any { it != 0 }) "Minecraft exited with code: $exitCodes" else null
}

internal fun completedGameTests(
  logText: String,
  completionMarkers: Map<String, String>,
  testFilter: String,
): List<String> {
  runtimeFailure(logText)?.let { throw GradleException(it) }
  val expected = expectedGameTests(completionMarkers, testFilter)
  val completed = matchedGameTests(logText, completionMarkers, testFilter)
  val missing = expected.keys - completed.toSet()
  if (completed.isEmpty() || missing.isNotEmpty()) {
    val reason =
      if (completed.isEmpty()) "No GameTests executed" else "GameTests missing completion markers"
    throw GradleException(
      "$reason: missing=$missing completed=$completed expected=${expected.keys}"
    )
  }
  return completed
}
