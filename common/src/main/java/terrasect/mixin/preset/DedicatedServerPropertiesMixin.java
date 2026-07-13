package terrasect.mixin.preset;

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

  // createDimensions took RegistryAccess on 1.21.1; every later target takes HolderLookup.Provider
  // (RegistryAccess extends HolderLookup.Provider at the source level, but Mixin matches the exact
  // parameter descriptor, not covariance, so an ungated HolderLookup.Provider-typed injector fails
  // descriptor validation — a hard crash at mixin apply time on 1.21.1, since neither injector
  // below
  // sets require=0).
  // spotless:off
  //? if >=1.21.11 {
  @Inject(method = "createDimensions", at = @At("RETURN"))
  private void terrasect$capturePresetId(
      HolderLookup.Provider provider, CallbackInfoReturnable<WorldDimensions> cir) {
    this.terrasect$presetId =
        ((PresetIdHolder) (Object) cir.getReturnValue()).terrasect$getPresetId();
  }
  //?} else {
  /*@Inject(method = "createDimensions", at = @At("RETURN"))
  private void terrasect$capturePresetId(
      net.minecraft.core.RegistryAccess provider, CallbackInfoReturnable<WorldDimensions> cir) {
    this.terrasect$presetId =
        ((PresetIdHolder) (Object) cir.getReturnValue()).terrasect$getPresetId();
  }
  *///?}
  // spotless:on

  @Override
  public @Nullable String terrasect$getPresetId() {
    return this.terrasect$presetId;
  }

  @Override
  public void terrasect$setPresetId(@Nullable String presetId) {
    this.terrasect$presetId = presetId;
  }
}
