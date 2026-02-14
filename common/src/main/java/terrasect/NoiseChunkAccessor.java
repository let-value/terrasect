package terrasect;

import net.minecraft.world.level.chunk.ChunkAccess;

public interface NoiseChunkAccessor {
  void terrasect$setChunkAccess(ChunkAccess chunkAccess);

  ChunkAccess terrasect$getChunkAccess();
}
