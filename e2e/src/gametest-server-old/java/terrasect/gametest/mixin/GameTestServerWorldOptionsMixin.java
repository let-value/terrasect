package terrasect.gametest.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// The vanilla gametest server hardcodes WorldOptions(0, false, false) — structures off. On
// old-paradigm versions Terrasect's forced-structure hook rides inside createStructures, which
// vanilla skips entirely when structures are disabled, so the smoke world must generate structures
// the way every default world does or assertForcedStart can never observe the production path.
@Mixin(GameTestServer.class)
public class GameTestServerWorldOptionsMixin {
  @ModifyArg(
      method = "<clinit>",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/level/levelgen/WorldOptions;<init>(JZZ)V"),
      index = 1)
  private static boolean terrasect$enableStructures(boolean generateStructures) {
    return true;
  }
}
