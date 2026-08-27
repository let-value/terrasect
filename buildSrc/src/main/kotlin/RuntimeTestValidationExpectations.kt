// Offline validation-expectations task for the runtime-test descriptor gate.
//
// The acceptance gate (:runtimeTestDescriptorValidate) proves the real matrix is valid. This task
// proves the SAME parse()/validate() pipeline REJECTS the four failure shapes the card calls out —
// malformed, unsupported, missing-lane, and header mismatch — plus that the real matrix still
// passes.
//
// It drives RuntimeTestDescriptors.loadAll() directly, so it exercises the real rejection code
// paths
// instead of a copy. There is no network access and no launch tooling involved: it only reads local
// YAML and runs the pure parse/validate logic in buildSrc. This task is opt-in and is NOT wired
// into
// CI, the build, or the launch pipeline, so it cannot alter release behaviour.

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

abstract class RuntimeTestValidationExpectationsTask : DefaultTask() {

  /** Path to the `name :: dir-relative-to-project-root :: expected-substring` expectations file. */
  @get:InputFile abstract val expectationsFile: RegularFileProperty

  /**
   * Project root, used to resolve the per-case fixture directories listed in the expectations file.
   */
  @get:InputDirectory @get:Optional abstract val projectRoot: DirectoryProperty

  /** The real runtime-tests matrix directory; loaded and asserted to PASS. */
  @get:InputDirectory @get:Optional abstract val positiveDir: DirectoryProperty

  @TaskAction
  fun run() {
    val propsFile =
      expectationsFile.orNull?.asFile ?: throw GradleException("expectationsFile is not set")
    val rootDir = (projectRoot.orNull?.asFile) ?: throw GradleException("projectRoot is not set")
    val posDir = positiveDir.orNull?.asFile

    val results = mutableListOf<String>()
    var failures = 0

    // Positive check: the real matrix must validate cleanly.
    if (posDir != null) {
      try {
        val manifests = RuntimeTestDescriptors.loadAll(posDir)
        results.add("PASS  positive-runtime-tests -> ${manifests.size} manifest(s) valid")
      } catch (e: Exception) {
        failures++
        results.add("FAIL  positive-runtime-tests -> unexpectedly rejected: ${e.message}")
      }
    }

    for (line in propsFile.readText().split("\n")) {
      val trimmed = line.trim()
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
      val parts = trimmed.split("::").map { it.trim() }
      if (parts.size != 3) {
        failures++
        results.add("FAIL  <config> -> malformed expectation line: '$trimmed'")
        continue
      }
      val name = parts[0]
      val relDir = parts[1]
      val expected = parts[2]

      if (expected == "OK") {
        val dir = rootDir.resolve(relDir).normalize()
        try {
          val manifests = RuntimeTestDescriptors.loadAll(dir)
          results.add("PASS  $name -> ${manifests.size} manifest(s) valid")
        } catch (e: Exception) {
          failures++
          results.add("FAIL  $name -> unexpectedly rejected: ${e.message}")
        }
        continue
      }

      val dir = rootDir.resolve(relDir).normalize()
      try {
        RuntimeTestDescriptors.loadAll(dir)
        failures++
        results.add("FAIL  $name -> expected rejection but loadAll succeeded for $relDir")
      } catch (e: Exception) {
        val msg = e.message ?: ""
        if (msg.contains(expected)) {
          results.add("PASS  $name -> rejected (message matched '$expected')")
        } else {
          failures++
          val firstLine = msg.split("\n").firstOrNull { it.isNotBlank() } ?: msg
          results.add("FAIL  $name -> rejected but message lacked '$expected'; got: $firstLine")
        }
      }
    }

    logger.lifecycle(
      "RuntimeTestDescriptorValidationExpectations: ${results.size} expectation(s), $failures failure(s)"
    )
    results.forEach { logger.lifecycle("  $it") }
    if (failures > 0) {
      throw GradleException(
        "RuntimeTestDescriptorValidationExpectations: $failures expectation(s) failed"
      )
    }
  }
}
