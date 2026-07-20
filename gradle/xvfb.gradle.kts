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

tasks
  .matching { it.name == "runClientGameTest" || it.name == "runGameTest" }
  .configureEach {
    if (needsXvfb) {
      dependsOn(startXvfb)
      finalizedBy(stopXvfb)
      (this as JavaExec).environment("DISPLAY", xvfbDisplay)
    }
  }