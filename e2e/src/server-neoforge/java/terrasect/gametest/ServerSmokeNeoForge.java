package terrasect.gametest;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@Mod("terrasect_server_tests")
public class ServerSmokeNeoForge {
  public ServerSmokeNeoForge() {
    ServerSmokeGuard.INSTANCE.installIfRequested();
    NeoForge.EVENT_BUS.addListener(this::started);
  }

  private void started(ServerStartedEvent event) {
    ServerSmokeGuard.run(event.getServer());
  }
}
