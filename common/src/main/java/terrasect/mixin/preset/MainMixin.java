package terrasect.mixin.preset;

import net.minecraft.core.Registry;
import net.minecraft.server.Main;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.extender.PresetIdHolder;

@Mixin(Main.class)
public class MainMixin {
  // createNewWorldData does not exist at all on 1.21.1 (dedicated-server preset propagation is
  // unavailable there — no injector may be compiled). Its return type's cookie changed from
  // WorldData (1.21.11) to a WorldDataAndGenSettings record wrapping WorldData (26.1+); generics
  // erase at the bytecode level so an ungated injector still applies on 26.1+, but casting the
  // cookie straight to WorldData would silently check the wrong object (the record wrapper, which
  // is never a PresetIdHolder) instead of unwrapping .data() first.
  // spotless:off
  //? if >=26.1 {
  @Inject(method = "createNewWorldData", at = @At("RETURN"))
  private static void terrasect$applyPresetToLevelData(
      DedicatedServerSettings dedicatedServerSettings,
      WorldLoader.DataLoadContext dataLoadContext,
      Registry<LevelStem> registry,
      boolean bl,
      boolean bl2,
      CallbackInfoReturnable<
              WorldLoader.DataLoadOutput<
                  net.minecraft.world.level.storage.LevelDataAndDimensions.WorldDataAndGenSettings>>
          cir) {
    var properties = ((PresetIdHolder) dedicatedServerSettings.getProperties());
    var presetId = properties.terrasect$getPresetId();

    var worldData = cir.getReturnValue().cookie().data();
    if (worldData instanceof PresetIdHolder extender) {
      extender.terrasect$setPresetId(presetId);
    }
  }
  //?} elif >=1.21.11 {
  /*@Inject(method = "createNewWorldData", at = @At("RETURN"))
  private static void terrasect$applyPresetToLevelData(
      DedicatedServerSettings dedicatedServerSettings,
      WorldLoader.DataLoadContext dataLoadContext,
      Registry<LevelStem> registry,
      boolean bl,
      boolean bl2,
      CallbackInfoReturnable<
              WorldLoader.DataLoadOutput<net.minecraft.world.level.storage.WorldData>>
          cir) {
    var properties = ((PresetIdHolder) dedicatedServerSettings.getProperties());
    var presetId = properties.terrasect$getPresetId();

    var worldData = cir.getReturnValue().cookie();
    if (worldData instanceof PresetIdHolder extender) {
      extender.terrasect$setPresetId(presetId);
    }
  }
  *///?}
  // spotless:on
}
