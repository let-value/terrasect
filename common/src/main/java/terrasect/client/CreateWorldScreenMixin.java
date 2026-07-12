package terrasect.client;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.compat.ResourceKeyCompat;
import terrasect.extender.PresetIdHolder;

@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {
  // createNewWorld's second parameter changed from WorldData to
  // LevelDataAndDimensions$WorldDataAndGenSettings (plus an Optional) in 26.x. Only the matching
  // injector may be compiled per version — the other fails descriptor validation at apply time.
  // spotless:off
  //? if >=26.1 {
  @Inject(
      method =
          "createNewWorld(Lnet/minecraft/core/LayeredRegistryAccess;Lnet/minecraft/world/level/storage/LevelDataAndDimensions$WorldDataAndGenSettings;Ljava/util/Optional;)Z",
      at = @At("HEAD"),
      require = 0)
  private void terrasect$rememberPresetIdFromWorldData(
      LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess,
      @Coerce Object worldDataAndGenSettings,
      @Coerce Object gameRules,
      CallbackInfoReturnable<Boolean> cir) {
    try {
      terrasect$rememberPresetId(
          (WorldData)
              worldDataAndGenSettings.getClass().getMethod("data").invoke(worldDataAndGenSettings));
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(
          "Unable to read world data from createNewWorld arguments", exception);
    }
  }
  //?} else {
  /*@Inject(
      method =
          "createNewWorld(Lnet/minecraft/core/LayeredRegistryAccess;Lnet/minecraft/world/level/storage/WorldData;)Z",
      at = @At("HEAD"),
      require = 0)
  private void terrasect$rememberPresetIdFromWorldData(
      LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess,
      WorldData worldData,
      CallbackInfoReturnable<Boolean> cir) {
    terrasect$rememberPresetId(worldData);
  }
  *///?}
  // spotless:on

  private void terrasect$rememberPresetId(WorldData worldData) {
    if (!(worldData instanceof PresetIdHolder extender)) {
      return;
    }

    CreateWorldScreen screen = (CreateWorldScreen) (Object) this;
    var worldType = screen.getUiState().getWorldType();
    var presetHolder = worldType.preset();
    String presetId =
        presetHolder != null
            ? presetHolder.unwrapKey().map(ResourceKeyCompat.INSTANCE::getKeyId).orElse(null)
            : null;
    extender.terrasect$setPresetId(presetId);
  }
}
