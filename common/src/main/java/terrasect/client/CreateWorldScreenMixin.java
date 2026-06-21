package terrasect.client;

import java.util.Optional;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.extender.PresetIdHolder;

@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {
  @Inject(
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

  @Inject(
      method =
          "createNewWorld(Lnet/minecraft/core/LayeredRegistryAccess;Lnet/minecraft/world/level/storage/LevelDataAndDimensions$WorldDataAndGenSettings;Ljava/util/Optional;)Z",
      at = @At("HEAD"),
      require = 0)
  private void terrasect$rememberPresetIdFromWorldDataAndGenSettings(
      LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess,
      @Coerce Object worldDataAndGenSettings,
      Optional<GameRules> gameRules,
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

  private void terrasect$rememberPresetId(WorldData worldData) {
    if (!(worldData instanceof PresetIdHolder extender)) {
      return;
    }

    CreateWorldScreen screen = (CreateWorldScreen) (Object) this;
    var worldType = screen.getUiState().getWorldType();
    var presetHolder = worldType.preset();
    var presetId =
        presetHolder != null
            ? presetHolder.unwrapKey().map(key -> key.identifier().toString()).orElse(null)
            : null;
    extender.terrasect$setPresetId(presetId);
  }
}
