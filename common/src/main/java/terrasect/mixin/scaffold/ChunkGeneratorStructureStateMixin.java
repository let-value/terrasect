package terrasect.mixin.scaffold;

import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import terrasect.extender.ChunkGeneratorStructureStateExtender;
import terrasect.generation.DimensionContext;

@Mixin(ChunkGeneratorStructureState.class)
public class ChunkGeneratorStructureStateMixin implements ChunkGeneratorStructureStateExtender {
  @Unique private DimensionContext terrasect$dimensionContext;

  @Override
  public DimensionContext terrasect$getDimensionContext() {
    return terrasect$dimensionContext;
  }

  @Override
  public void terrasect$setDimensionContext(DimensionContext context) {
    terrasect$dimensionContext = context;
  }
}
