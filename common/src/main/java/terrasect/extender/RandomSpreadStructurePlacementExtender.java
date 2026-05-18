package terrasect.extender;

import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

public interface RandomSpreadStructurePlacementExtender extends StructurePlacementExtender {
  void terrasect$setSpacing(int spacing);

  void terrasect$setSeparation(int separation);

  void terrasect$setSpreadType(RandomSpreadType spreadType);
}
