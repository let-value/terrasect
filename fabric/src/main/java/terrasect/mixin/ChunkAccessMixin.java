package terrasect.mixin;

import java.util.function.Function;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.ChunkAccessExtender;
import terrasect.NoiseChunkExtender;
import terrasect.cache.ChunkCache;

@Mixin(ChunkAccess.class)
public class ChunkAccessMixin implements ChunkAccessExtender {
  @Unique private ChunkCache terrasect$Cache;
  @Unique private Level terrasect$level;

  @Override
  public ChunkCache terrasect$getCache() {
    return this.terrasect$Cache;
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
  }

  @Inject(method = "getOrCreateNoiseChunk", at = @At("RETURN"))
  private void terrasect$attachToNoiseChunk(
      Function<ChunkAccess, NoiseChunk> factory, CallbackInfoReturnable<NoiseChunk> cir) {
    terrasect$Cache = new ChunkCache(this);

    var noiseChunk = cir.getReturnValue();
    ((NoiseChunkExtender) noiseChunk).terrasect$setChunk(this);
  }

  @Override
  public ChunkAccess terrasect$getChunk() {
    return (ChunkAccess) (Object) this;
  }
}
