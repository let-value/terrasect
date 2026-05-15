package terrasect.mixin.scaffold;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import terrasect.extender.ChunkAccessExtender;
import terrasect.generation.ChunkContext;

@Mixin(ChunkAccess.class)
public class ChunkAccessMixin implements ChunkAccessExtender {
  @Unique private ChunkContext terrasect$context;
  @Unique private Level terrasect$level;

  @Override
  public ChunkContext terrasect$getContext() {
    return this.terrasect$context;
  }

  @Override
  public Level terrasect$getLevel() {
    return this.terrasect$level;
  }

  @Inject(method = "<init>", at = @At("RETURN"))
  private void terrasect$captureLevel(
      ChunkPos chunkPos,
      net.minecraft.world.level.chunk.UpgradeData upgradeData,
      LevelHeightAccessor levelHeightAccessor,
      PalettedContainerFactory palettedContainerFactory,
      long inhabitedTime,
      LevelChunkSection[] sections,
      BlendingData blendingData,
      CallbackInfo ci) {

    if (levelHeightAccessor instanceof Level level) {
      this.terrasect$level = level;
    }

    terrasect$context = new ChunkContext(this, chunkPos);
  }

  @Override
  public ChunkAccess terrasect$getChunk() {
    return (ChunkAccess) (Object) this;
  }
}
