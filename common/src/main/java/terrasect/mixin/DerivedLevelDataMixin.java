package terrasect.mixin;

import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import terrasect.extender.PresetIdHolder;

@Mixin(DerivedLevelData.class)
public abstract class DerivedLevelDataMixin implements PresetIdHolder {
  @Shadow @Final private WorldData worldData;

  @Override
  public @Nullable String terrasect$getPresetId() {
    if (this.worldData instanceof PresetIdHolder extender) {
      return extender.terrasect$getPresetId();
    }
    return null;
  }

  @Override
  public void terrasect$setPresetId(@Nullable String presetId) {
    if (this.worldData instanceof PresetIdHolder extender) {
      extender.terrasect$setPresetId(presetId);
    }
  }
}
