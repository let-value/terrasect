package terrasect.gametest.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

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

  // GameTestServer otherwise hardcodes the FLAT world preset, whose FixedBiomeSource cannot
  // exercise the MultiNoise biome-selection path. Keep the old server gametest lane on a normal
  // overworld so its biome assertion covers the same production source as the client lanes.
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
