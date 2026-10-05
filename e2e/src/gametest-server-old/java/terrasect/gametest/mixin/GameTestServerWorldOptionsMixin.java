package terrasect.gametest.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

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

  @Redirect(
      method = "method_40377",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/core/Registry;getHolderOrThrow(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference;"))
  private static net.minecraft.core.Holder.Reference<WorldPreset> terrasect$useNormalPreset(
      net.minecraft.core.Registry<WorldPreset> registry, ResourceKey<WorldPreset> ignored) {
    return registry.getHolderOrThrow(WorldPresets.NORMAL);
  }
}
