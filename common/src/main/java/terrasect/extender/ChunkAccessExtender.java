package terrasect.extender;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import terrasect.generation.ChunkContext;

public interface ChunkAccessExtender {
  ChunkContext terrasect$getCache();

  ChunkAccess terrasect$getChunk();

  Level terrasect$getLevel();
}
