package terrasect.gametest

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

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
          System.err.println("Client gametests stalled for 60 seconds")
          Runtime.getRuntime().halt(1)
        }
      }
    }
    watchdog.isDaemon = true
    watchdog.name = "terrasect-e2e-compat-exit-watchdog"
    watchdog.start()
  }
}
