package terrasect.mixin.structure;

import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(JigsawStructure.class)
public interface JigsawStructureAccessor {
  // spotless:off
  //? if >=1.21.11 {
  @Accessor("maxDistanceFromCenter")
  JigsawStructure.MaxDistance terrasect$maxDistanceFromCenter();
  //?} else {
  /*@Accessor("maxDistanceFromCenter")
  int terrasect$maxDistanceFromCenter();
  *///?}
  // spotless:on
}
