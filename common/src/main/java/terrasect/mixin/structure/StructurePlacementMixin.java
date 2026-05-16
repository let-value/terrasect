package terrasect.mixin.structure;

import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
  public int terrasect$salt() {
    return salt();
  }

  @Override
  public Vec3i terrasect$locateOffset() {
    return locateOffset();
  }

  @Override
  public StructurePlacement.FrequencyReductionMethod terrasect$frequencyReductionMethod() {
    return frequencyReductionMethod();
  }

  @Override
  public Optional<StructurePlacement.ExclusionZone> terrasect$exclusionZone() {
    return exclusionZone();
  }
}
