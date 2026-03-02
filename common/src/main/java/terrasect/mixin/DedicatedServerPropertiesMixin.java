package terrasect.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.extender.PresetIdHolder;

@Mixin(DedicatedServerProperties.class)
public class DedicatedServerPropertiesMixin implements PresetIdHolder {
  @Unique @Nullable private String terrasect$presetId;

  @Inject(method = "createDimensions", at = @At("RETURN"))
  private void terrasect$capturePresetId(
      HolderLookup.Provider provider, CallbackInfoReturnable<WorldDimensions> cir) {
    this.terrasect$presetId =
        ((PresetIdHolder) (Object) cir.getReturnValue()).terrasect$getPresetId();
  }

  @Override
  public @Nullable String terrasect$getPresetId() {
    return this.terrasect$presetId;
  }

  @Override
  public void terrasect$setPresetId(@Nullable String presetId) {
    this.terrasect$presetId = presetId;
  }
}
