package terrasect.mixin;

import java.util.List;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.NoiseChunkAccessor;
import terrasect.SamplerAccessor;

@Mixin(NoiseChunk.class)
public class NoiseChunkAccessorMixin implements NoiseChunkAccessor {

  @Unique private ChunkAccess terrasect$chunkAccess;

  @Override
  public void terrasect$setChunkAccess(ChunkAccess chunkAccess) {
    this.terrasect$chunkAccess = chunkAccess;
  }

  @Override
  public ChunkAccess terrasect$getChunkAccess() {
    return this.terrasect$chunkAccess;
  }

  @Inject(method = "cachedClimateSampler", at = @At("RETURN"))
  private void terrasect$setSamplerChunkAccess(
      NoiseRouter noiseRouter,
      List<Climate.ParameterPoint> spawnTarget,
      CallbackInfoReturnable<Climate.Sampler> cir) {
    var sampler = cir.getReturnValue();
    ((SamplerAccessor) sampler).terrasect$setChunkAccess(this.terrasect$chunkAccess);
  }
}
