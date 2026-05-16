package terrasect.mixin.structure;

import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import terrasect.extender.StructurePlacementExtender;

@Mixin(StructurePlacement.class)
public abstract class StructurePlacementMixin implements StructurePlacementExtender {
  @Shadow
  protected abstract float frequency();

  @Shadow
  protected abstract int salt();

  @Shadow
  protected abstract Vec3i locateOffset();

  @Shadow
  protected abstract StructurePlacement.FrequencyReductionMethod frequencyReductionMethod();

  @Shadow
  protected abstract Optional<StructurePlacement.ExclusionZone> exclusionZone();

  @Override
  public float terrasect$frequency() {
    return frequency();
  }

  @Override
  @Accessor("frequency")
  @Mutable
  public abstract void terrasect$setFrequency(float frequency);

  @Override
  public int terrasect$salt() {
    return salt();
  }

  @Override
  @Accessor("salt")
  @Mutable
  public abstract void terrasect$setSalt(int salt);

  @Override
  public Vec3i terrasect$locateOffset() {
    return locateOffset();
  }

  @Override
  @Accessor("locateOffset")
  @Mutable
  public abstract void terrasect$setLocateOffset(Vec3i locateOffset);

  @Override
  public StructurePlacement.FrequencyReductionMethod terrasect$frequencyReductionMethod() {
    return frequencyReductionMethod();
  }

  @Override
  @Accessor("frequencyReductionMethod")
  @Mutable
  public abstract void terrasect$setFrequencyReductionMethod(
      StructurePlacement.FrequencyReductionMethod frequencyReductionMethod);

  @Override
  public Optional<StructurePlacement.ExclusionZone> terrasect$exclusionZone() {
    return exclusionZone();
  }

  @Override
  @Accessor("exclusionZone")
  @Mutable
  public abstract void terrasect$setExclusionZone(
      Optional<StructurePlacement.ExclusionZone> exclusionZone);
}
