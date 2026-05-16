package terrasect.mixin.structure;

import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import terrasect.extender.RandomSpreadStructurePlacementExtender;

@Mixin(RandomSpreadStructurePlacement.class)
public abstract class RandomSpreadStructurePlacementMixin
    implements RandomSpreadStructurePlacementExtender {

  @Shadow
  public abstract RandomSpreadType spreadType();

  @Override
  @Accessor("spacing")
  @Mutable
  public abstract void terrasect$setSpacing(int spacing);

  @Override
  @Accessor("separation")
  @Mutable
  public abstract void terrasect$setSeparation(int separation);

  @Override
  @Accessor("spreadType")
  @Mutable
  public abstract void terrasect$setSpreadType(RandomSpreadType spreadType);

  @Override
  public RandomSpreadStructurePlacement terrasect$withOverrides(
      int spacing, int separation, float frequency) {
    terrasect$setFrequency(frequency);
    terrasect$setSpacing(spacing);
    terrasect$setSeparation(separation);
    return (RandomSpreadStructurePlacement) (Object) this;
  }
}
