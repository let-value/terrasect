package terrasect.gametest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class ServerSmokeFabric implements ModInitializer {
  @Override
  public void onInitialize() {
    ServerSmokeGuard.INSTANCE.installIfRequested();
    ServerLifecycleEvents.SERVER_STARTED.register(ServerSmokeGuard::run);
  }
}
