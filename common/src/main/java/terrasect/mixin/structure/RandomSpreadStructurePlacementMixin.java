package terrasect.mixin.structure;

import java.lang.reflect.Field;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import terrasect.extender.RandomSpreadStructurePlacementExtender;
import terrasect.extender.StructurePlacementExtender;

@Mixin(RandomSpreadStructurePlacement.class)
public abstract class RandomSpreadStructurePlacementMixin
    implements RandomSpreadStructurePlacementExtender {
  private static final sun.misc.Unsafe UNSAFE = initUnsafe();

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

  private static sun.misc.Unsafe initUnsafe() {
    try {
      Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
      field.setAccessible(true);
      return (sun.misc.Unsafe) field.get(null);
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Override
  public RandomSpreadStructurePlacement terrasect$withOverrides(
      int spacing, int separation, float frequency) {
    try {
      RandomSpreadStructurePlacement copy =
          (RandomSpreadStructurePlacement) UNSAFE.allocateInstance(RandomSpreadStructurePlacement.class);
      StructurePlacementExtender source = (StructurePlacementExtender) (Object) this;
      StructurePlacementExtender target = (StructurePlacementExtender) (Object) copy;

      target.terrasect$setLocateOffset(source.terrasect$locateOffset());
      target.terrasect$setFrequencyReductionMethod(source.terrasect$frequencyReductionMethod());
      target.terrasect$setFrequency(frequency);
      target.terrasect$setSalt(source.terrasect$salt());
      target.terrasect$setExclusionZone(source.terrasect$exclusionZone());

      RandomSpreadStructurePlacementExtender mutable =
          (RandomSpreadStructurePlacementExtender) (Object) copy;
      mutable.terrasect$setSpacing(spacing);
      mutable.terrasect$setSeparation(separation);
      mutable.terrasect$setSpreadType(spreadType());
      return copy;
    } catch (InstantiationException e) {
      throw new RuntimeException("Failed to allocate RandomSpreadStructurePlacement copy", e);
    }
  }
}
