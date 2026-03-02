package terrasect.client;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.extender.PresetIdHolder;

@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {
  @Inject(method = "createNewWorld", at = @At("HEAD"))
  private void terrasect$rememberPresetId(
      LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess,
      WorldData worldData,
      CallbackInfoReturnable<Boolean> cir) {
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
