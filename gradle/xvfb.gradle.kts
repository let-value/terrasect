// Shared by build.e2e.gradle.kts and build.e2e-compat.gradle.kts via `apply(from = ...)`. Client
// gametests need a real display; CI runners on Linux typically don't have one, so start a
// throwaway Xvfb server around the run when Xvfb is available and no DISPLAY is already set.
// No-ops locally (macOS/Windows dev machines, or Linux with a real display already attached).

val xvfbDisplay = ":99"
var xvfbProcess: Process? = null
val isLinux = System.getProperty("os.name").lowercase().contains("linux")
val needsXvfb = isLinux && System.getenv("DISPLAY").isNullOrBlank()

val startXvfb by tasks.registering {
  onlyIf {
    needsXvfb &&
      providers
        .exec {
          commandLine("which", "Xvfb")
          isIgnoreExitValue = true
        }
        .standardOutput
        .asText
        .get()
        .isNotBlank()
  }
  doLast {
    xvfbProcess =
      ProcessBuilder("Xvfb", xvfbDisplay, "-screen", "0", "1920x1080x24")
        .redirectErrorStream(true)
        .start()
    Thread.sleep(500)
  }
}

val stopXvfb by tasks.registering {
  doLast {
    xvfbProcess?.destroy()
    xvfbProcess = null
  }
}

tasks.named<JavaExec>("runClientGameTest") {
  if (needsXvfb) {
    dependsOn(startXvfb)
    finalizedBy(stopXvfb)
    environment("DISPLAY", xvfbDisplay)
  }
}
