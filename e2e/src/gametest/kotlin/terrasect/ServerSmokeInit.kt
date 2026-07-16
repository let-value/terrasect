package terrasect

import net.fabricmc.api.ModInitializer

// Runs at mod init (before any world loads) so the forced preset is in place when the gametest
// server generates its overworld. Registered as a `main` entrypoint; the force itself is gated on
// ServerSmokeGuard.FORCE_PROPERTY so non-gametest launches are unaffected.
class ServerSmokeInit : ModInitializer {
  override fun onInitialize() {
    ServerSmokeGuard.installIfRequested()
  }
}
