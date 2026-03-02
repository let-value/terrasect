package terrasect.mixin.preset;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import terrasect.extender.PresetIdHolder;

@Mixin(targets = "net.minecraft.server.dedicated.DedicatedServerProperties$WorldDimensionData")
public class DedicatedServerPropertiesWorldDimensionDataMixin {
  @Unique @Nullable private String terrasect$presetId;

  @Inject(
      method = "create",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/presets/WorldPreset;createWorldDimensions()Lnet/minecraft/world/level/levelgen/WorldDimensions;",
              shift = At.Shift.BEFORE),
      locals = LocalCapture.CAPTURE_FAILSOFT)
  private void terrasect$capturePresetId(
      HolderLookup.Provider provider,
      CallbackInfoReturnable<WorldDimensions> cir,
      HolderLookup<WorldPreset> holderLookup,
      Holder.Reference<WorldPreset> reference,
      Holder<WorldPreset> holder) {
    this.terrasect$presetId =
        holder.unwrapKey().map(key -> key.identifier().toString()).orElse(null);
  }

  @Inject(method = "create", at = @At("RETURN"))
  private void terrasect$attachPresetId(
      HolderLookup.Provider provider, CallbackInfoReturnable<WorldDimensions> cir) {
    ((PresetIdHolder) (Object) cir.getReturnValue()).terrasect$setPresetId(this.terrasect$presetId);
  }
}
