package terrasect.extender;

import terrasect.generation.DimensionContext;

public interface ChunkGeneratorStructureStateExtender {
  DimensionContext terrasect$getDimensionContext();

  void terrasect$setDimensionContext(DimensionContext context);
}
