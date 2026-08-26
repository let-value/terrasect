package terrasect.extender;

import terrasect.generation.ChunkContext;
import terrasect.generation.DimensionContext;

public interface ClimateParameterListExtender {
  boolean terrasect$hasPositionalLookup();

  void terrasect$setQueryContext(DimensionContext context, ChunkContext chunkContext);

  void terrasect$clearQueryContext();
}
