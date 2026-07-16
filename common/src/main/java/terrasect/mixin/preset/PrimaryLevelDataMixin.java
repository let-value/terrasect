package terrasect.mixin.preset;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelSettings;
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

  // PrimaryLevelData.parse changed signature across versions: 26.1 dropped the WorldOptions
  // parameter; 1.20.1 (pre-1.21) additionally carries DataFixer/version/CompoundTag/LevelVersion
  // parameters. Only the overload matching the target version may be injected; verified against
  // decompiled bytecode per version.
  // spotless:off
  //? if >=26.1 {
  @SuppressWarnings("deprecation")
  @Inject(
      method = "parse(Lcom/mojang/serialization/Dynamic;Lnet/minecraft/world/level/LevelSettings;Lnet/minecraft/world/level/storage/PrimaryLevelData$SpecialWorldProperty;Lcom/mojang/serialization/Lifecycle;)Lnet/minecraft/world/level/storage/PrimaryLevelData;",
      at = @At("RETURN"),
      require = 0)
  private static void terrasect$capturePresetId(
      Dynamic<?> dynamic,
      LevelSettings levelSettings,
      PrimaryLevelData.SpecialWorldProperty specialWorldProperty,
      Lifecycle lifecycle,
      CallbackInfoReturnable<PrimaryLevelData> cir) {
    terrasect$readPresetId(dynamic, cir);
  }
  //?} elif >=1.21.1 {
  /*@SuppressWarnings("deprecation")
  @Inject(
      method = "parse(Lcom/mojang/serialization/Dynamic;Lnet/minecraft/world/level/LevelSettings;Lnet/minecraft/world/level/storage/PrimaryLevelData$SpecialWorldProperty;Lnet/minecraft/world/level/levelgen/WorldOptions;Lcom/mojang/serialization/Lifecycle;)Lnet/minecraft/world/level/storage/PrimaryLevelData;",
      at = @At("RETURN"),
      require = 0)
  private static void terrasect$capturePresetId(
      Dynamic<?> dynamic,
      LevelSettings levelSettings,
      PrimaryLevelData.SpecialWorldProperty specialWorldProperty,
      net.minecraft.world.level.levelgen.WorldOptions worldOptions,
      Lifecycle lifecycle,
      CallbackInfoReturnable<PrimaryLevelData> cir) {
    terrasect$readPresetId(dynamic, cir);
  }
  *///?} else {
  /*@SuppressWarnings("deprecation")
  @Inject(
      method = "parse(Lcom/mojang/serialization/Dynamic;Lcom/mojang/datafixers/DataFixer;ILnet/minecraft/nbt/CompoundTag;Lnet/minecraft/world/level/LevelSettings;Lnet/minecraft/world/level/storage/LevelVersion;Lnet/minecraft/world/level/storage/PrimaryLevelData$SpecialWorldProperty;Lnet/minecraft/world/level/levelgen/WorldOptions;Lcom/mojang/serialization/Lifecycle;)Lnet/minecraft/world/level/storage/PrimaryLevelData;",
      at = @At("RETURN"),
      require = 0)
  private static void terrasect$capturePresetId(
      Dynamic<?> dynamic,
      com.mojang.datafixers.DataFixer dataFixer,
      int version,
      CompoundTag worldData,
      LevelSettings levelSettings,
      net.minecraft.world.level.storage.LevelVersion levelVersion,
      PrimaryLevelData.SpecialWorldProperty specialWorldProperty,
      net.minecraft.world.level.levelgen.WorldOptions worldOptions,
      Lifecycle lifecycle,
      CallbackInfoReturnable<PrimaryLevelData> cir) {
    terrasect$readPresetId(dynamic, cir);
  }
  *///?}
  // spotless:on

  @Unique
  private static void terrasect$readPresetId(
      Dynamic<?> dynamic, CallbackInfoReturnable<PrimaryLevelData> cir) {
    var presetId = dynamic.get(TERRASECT_PRESET).asString("").trim();
    var extender = ((PresetIdHolder) cir.getReturnValue());
    extender.terrasect$setPresetId(presetId.isEmpty() ? null : presetId);
  }

  // setTagData's signature changed in 26.1 from (RegistryAccess, CompoundTag, CompoundTag) to
  // (CompoundTag, UUID). Verified against decompiled bytecode per version.
  // spotless:off
  //? if >=26.1 {
  @Inject(
      method = "setTagData(Lnet/minecraft/nbt/CompoundTag;Ljava/util/UUID;)V",
      at = @At("TAIL"),
      require = 0)
  private void terrasect$writePresetIdTag(CompoundTag compoundTag, UUID worldUuid, CallbackInfo ci) {
    terrasect$writePresetId(compoundTag);
  }
  //?} else {
  /*@Inject(
      method = "setTagData(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/nbt/CompoundTag;)V",
      at = @At("TAIL"),
      require = 0)
  private void terrasect$writePresetIdTag(
      net.minecraft.core.RegistryAccess registryAccess,
      CompoundTag compoundTag,
      @Nullable CompoundTag compoundTag2,
      CallbackInfo ci) {
    terrasect$writePresetId(compoundTag);
  }
  *///?}
  // spotless:on

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
