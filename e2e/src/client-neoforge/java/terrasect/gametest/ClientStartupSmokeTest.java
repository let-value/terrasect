package terrasect.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = "terrasect_server_tests", value = Dist.CLIENT)
public class ClientStartupSmokeTest {
  @SubscribeEvent
  public static void screenOpened(ScreenEvent.Init.Post event) {
    if (!"ClientStartupSmokeTest".equals(System.getProperty("test"))) return;
    LoggerFactory.getLogger("ClientStartupSmokeTest")
        .info("Client screen: {}", event.getScreen().getClass().getName());
    if (event.getScreen() instanceof AccessibilityOnboardingScreen) {
      event.getScreen().onClose();
      return;
    }
    if (!(event.getScreen() instanceof TitleScreen)) return;
    try {
      ServerSmokeGuard.assertConfiguration(FMLPaths.CONFIGDIR.get());
      LoggerFactory.getLogger("ClientStartupSmokeTest").info("client startup smoke: OK");
      Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
    } catch (Throwable failure) {
      LoggerFactory.getLogger("ClientStartupSmokeTest")
          .error("Client startup smoke failed", failure);
      Runtime.getRuntime().halt(1);
    }
  }
}
