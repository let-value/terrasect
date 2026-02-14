package terrasect;

import net.minecraft.world.level.chunk.ChunkAccess;

public interface SamplerAccessor {
  void terrasect$setChunkAccess(ChunkAccess chunkAccess);

  ChunkAccess terrasect$chunkAccess();
}
