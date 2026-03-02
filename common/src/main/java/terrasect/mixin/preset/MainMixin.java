package terrasect.mixin.preset;

import net.minecraft.core.Registry;
import net.minecraft.server.Main;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.extender.PresetIdHolder;

@Mixin(Main.class)
public class MainMixin {
  @Inject(method = "createNewWorldData", at = @At("RETURN"))
  private static void terrasect$applyPresetToLevelData(
      DedicatedServerSettings dedicatedServerSettings,
      WorldLoader.DataLoadContext dataLoadContext,
      Registry<LevelStem> registry,
      boolean bl,
      boolean bl2,
      CallbackInfoReturnable<WorldLoader.DataLoadOutput<WorldData>> cir) {
    var properties = ((PresetIdHolder) dedicatedServerSettings.getProperties());
    var presetId = properties.terrasect$getPresetId();

    var worldData = cir.getReturnValue().cookie();
    if (worldData instanceof PresetIdHolder extender) {
      extender.terrasect$setPresetId(presetId);
    }
  }
}
