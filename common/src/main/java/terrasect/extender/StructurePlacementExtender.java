package terrasect.extender;

import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

public interface StructurePlacementExtender {
  float terrasect$frequency();

  int terrasect$salt();

  Vec3i terrasect$locateOffset();

  StructurePlacement.FrequencyReductionMethod terrasect$frequencyReductionMethod();

  Optional<StructurePlacement.ExclusionZone> terrasect$exclusionZone();
}
