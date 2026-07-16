package terrasect

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.impl.client.gametest.threading.ThreadingImpl

// DistantHorizons (present only in e2e-compat) deadlocks the client Render thread inside
// `game.close()` while closing its SQLite repos on world unload — so after a client gametest
// finishes, ticking freezes, the test thread blocks waiting on the render thread, and no
// lifecycle event or framework "done" signal can ever fire. The fabric-client-gametest runner
// relies on natural JVM shutdown and never force-exits, so Loom's runClientGameTest eventually
// SIGTERMs a fully-passing run (exit 143) after a very long wait.
//
// This watchdog runs on its own daemon thread (immune to the frozen render/test threads): once
// the client has started ticking, a long tick stall means the run has stopped making progress, so
// hard-halt the JVM before the wait drags on. The exit code comes from the runner's own recorded
// state (testFailureException / isGameCrashed) so a failed or crashed run still exits non-zero.
// Residual risk: a test that hangs mid-execution without recording a failure would exit 0; that is
// far less likely than the observed cleanup deadlock and is the accepted tradeoff for these
// compat smoke tests.
@Suppress("UnstableApiUsage")
object CompatGameTestExit : ClientModInitializer {
  private const val STALL_LIMIT_NANOS = 60_000_000_000L

  @Volatile private var lastTickNanos = 0L

  override fun onInitializeClient() {
    ClientTickEvents.END_CLIENT_TICK.register { lastTickNanos = System.nanoTime() }

    val watchdog = Thread {
      while (lastTickNanos == 0L) Thread.sleep(200)
      while (true) {
        Thread.sleep(1_000)
        if (System.nanoTime() - lastTickNanos >= STALL_LIMIT_NANOS) {
          val passed = ThreadingImpl.testFailureException == null && !ThreadingImpl.isGameCrashed()
          Runtime.getRuntime().halt(if (passed) 0 else 1)
        }
      }
    }
    watchdog.isDaemon = true
    watchdog.name = "terrasect-e2e-compat-exit-watchdog"
    watchdog.start()
  }
}
