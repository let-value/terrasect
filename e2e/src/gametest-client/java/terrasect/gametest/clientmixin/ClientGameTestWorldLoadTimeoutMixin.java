package terrasect.gametest.clientmixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// fabric's waitForWorldLoad gives world creation a fixed 1200-client-tick budget with no config
// knob; slow CI runners blow it while the integrated server prepares the spawn area (observed
// deterministically on 1.21.11 GitHub runners). Raise the budget — the assertion stays, so a
// genuine hang still fails, just later. Same constant on every client-capable fabric-api (4.3.5
// through 5.1.0); require = 0 so a future fabric-api refactor degrades back to the stock budget
// instead of crashing the suite.
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.gametest.util.ClientGameTestImpl", remap = false)
public class ClientGameTestWorldLoadTimeoutMixin {
  @ModifyConstant(method = "waitForWorldLoad", constant = @Constant(intValue = 1200), require = 0)
  private static int terrasect$extendWorldLoadBudget(int ticks) {
    return 6000;
  }
}
