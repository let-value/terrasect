package terrasect

// CI-only diagnostics: gametest launches on GitHub runners have wedged with no error output
// (spawn preparation frozen for 40+ minutes), and there is no way to attach a debugger there.
// Dumps every thread's stack to stdout periodically so a hung run explains itself in the job log.
object StallWatchdog {
  fun installIfRequested() {
    if (System.getenv("TERRASECT_WATCHDOG").isNullOrBlank()) return
    val thread =
      Thread(
        {
          while (true) {
            Thread.sleep(4 * 60 * 1000L)
            val dump = buildString {
              appendLine("=== TERRASECT WATCHDOG THREAD DUMP ===")
              for ((t, stack) in Thread.getAllStackTraces()) {
                appendLine("\"${t.name}\" daemon=${t.isDaemon} state=${t.state}")
                stack.forEach { appendLine("    at $it") }
              }
              appendLine("=== END WATCHDOG DUMP ===")
            }
            println(dump)
          }
        },
        "terrasect-stall-watchdog",
      )
    thread.isDaemon = true
    thread.start()
  }
}
