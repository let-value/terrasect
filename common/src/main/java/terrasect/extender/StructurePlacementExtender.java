package terrasect.extender;

import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

public interface StructurePlacementExtender {
  float terrasect$frequency();

  void terrasect$setFrequency(float frequency);

  int terrasect$salt();

  void terrasect$setSalt(int salt);

  Vec3i terrasect$locateOffset();

  void terrasect$setLocateOffset(Vec3i locateOffset);

  StructurePlacement.FrequencyReductionMethod terrasect$frequencyReductionMethod();

  void terrasect$setFrequencyReductionMethod(
      StructurePlacement.FrequencyReductionMethod frequencyReductionMethod);

  Optional<StructurePlacement.ExclusionZone> terrasect$exclusionZone();

  void terrasect$setExclusionZone(Optional<StructurePlacement.ExclusionZone> exclusionZone);
}
