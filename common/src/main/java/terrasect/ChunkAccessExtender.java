package terrasect;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import terrasect.cache.Cache;

public interface ChunkAccessExtender {
  Cache terrasect$getCache();

  ChunkAccess terrasect$getChunk();

  Level terrasect$getLevel();
}
