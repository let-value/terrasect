package terrasect.mixin;

import net.minecraft.world.level.levelgen.WorldDimensions;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import terrasect.extender.PresetIdHolder;

@Mixin(WorldDimensions.class)
public class WorldDimensionsMixin implements PresetIdHolder {
  @Unique @Nullable private String terrasect$presetId;

  @Override
  public @Nullable String terrasect$getPresetId() {
    return this.terrasect$presetId;
  }

  @Override
  public void terrasect$setPresetId(@Nullable String presetId) {
    this.terrasect$presetId = presetId;
  }
}
