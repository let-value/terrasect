package terrasect.mixin;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.extender.PresetIdHolder;

@Mixin(PrimaryLevelData.class)
public class PrimaryLevelDataMixin implements PresetIdHolder {
  @Unique private static final String TERRASECT_PRESET = "TerrasectPreset";
  @Unique @Nullable private String terrasect$presetId;

  @SuppressWarnings("deprecation")
  @Inject(method = "parse", at = @At("RETURN"))
  private static void terrasect$readPresetId(
      Dynamic<?> dynamic,
      LevelSettings levelSettings,
      PrimaryLevelData.SpecialWorldProperty specialWorldProperty,
      WorldOptions worldOptions,
      Lifecycle lifecycle,
      CallbackInfoReturnable<PrimaryLevelData> cir) {
    var presetId = dynamic.get(TERRASECT_PRESET).asString("").trim();
    var extender = ((PresetIdHolder) cir.getReturnValue());
    extender.terrasect$setPresetId(presetId.isEmpty() ? null : presetId);
  }

  @Inject(method = "setTagData", at = @At("TAIL"))
  private void terrasect$writePresetId(
      RegistryAccess registryAccess,
      CompoundTag compoundTag,
      @Nullable CompoundTag compoundTag2,
      CallbackInfo ci) {
    var presetId = this.terrasect$presetId;
    if (presetId == null || presetId.isEmpty()) {
      compoundTag.remove(TERRASECT_PRESET);
    } else {
      compoundTag.putString(TERRASECT_PRESET, presetId);
    }
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
