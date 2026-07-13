package terrasect.mixin.preset;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import java.util.UUID;
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
  @Inject(
      method =
          "parse(Lcom/mojang/serialization/Dynamic;Lnet/minecraft/world/level/LevelSettings;Lnet/minecraft/world/level/storage/PrimaryLevelData$SpecialWorldProperty;Lnet/minecraft/world/level/levelgen/WorldOptions;Lcom/mojang/serialization/Lifecycle;)Lnet/minecraft/world/level/storage/PrimaryLevelData;",
      at = @At("RETURN"),
      require = 0)
  private static void terrasect$readPresetIdWithWorldOptions(
      Dynamic<?> dynamic,
      LevelSettings levelSettings,
      PrimaryLevelData.SpecialWorldProperty specialWorldProperty,
      WorldOptions worldOptions,
      Lifecycle lifecycle,
      CallbackInfoReturnable<PrimaryLevelData> cir) {
    terrasect$readPresetId(dynamic, cir);
  }

  @SuppressWarnings("deprecation")
  @Inject(
      method =
          "parse(Lcom/mojang/serialization/Dynamic;Lnet/minecraft/world/level/LevelSettings;Lnet/minecraft/world/level/storage/PrimaryLevelData$SpecialWorldProperty;Lcom/mojang/serialization/Lifecycle;)Lnet/minecraft/world/level/storage/PrimaryLevelData;",
      at = @At("RETURN"),
      require = 0)
  private static void terrasect$readPresetIdWithoutWorldOptions(
      Dynamic<?> dynamic,
      LevelSettings levelSettings,
      PrimaryLevelData.SpecialWorldProperty specialWorldProperty,
      Lifecycle lifecycle,
      CallbackInfoReturnable<PrimaryLevelData> cir) {
    terrasect$readPresetId(dynamic, cir);
  }

  @Unique
  private static void terrasect$readPresetId(
      Dynamic<?> dynamic, CallbackInfoReturnable<PrimaryLevelData> cir) {
    var presetId = dynamic.get(TERRASECT_PRESET).asString("").trim();
    var extender = ((PresetIdHolder) cir.getReturnValue());
    extender.terrasect$setPresetId(presetId.isEmpty() ? null : presetId);
  }

  @Inject(
      method =
          "setTagData(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/nbt/CompoundTag;)V",
      at = @At("TAIL"),
      require = 0)
  private void terrasect$writePresetIdWithRegistryAccess(
      RegistryAccess registryAccess,
      CompoundTag compoundTag,
      @Nullable CompoundTag compoundTag2,
      CallbackInfo ci) {
    terrasect$writePresetId(compoundTag);
  }

  @Inject(
      method = "setTagData(Lnet/minecraft/nbt/CompoundTag;Ljava/util/UUID;)V",
      at = @At("TAIL"),
      require = 0)
  private void terrasect$writePresetIdWithWorldUuid(
      CompoundTag compoundTag, UUID worldUuid, CallbackInfo ci) {
    terrasect$writePresetId(compoundTag);
  }

  @Unique
  private void terrasect$writePresetId(CompoundTag compoundTag) {
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
