package terrasect;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;

public interface ChunkAccessExtender {
  ChunkAccess terrasect$getChunk();

  Level terrasect$getLevel();
}
