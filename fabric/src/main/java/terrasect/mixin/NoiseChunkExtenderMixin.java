package terrasect.mixin;

import java.util.List;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.ChunkAccessExtender;
import terrasect.ClimateSamplerExtender;
import terrasect.NoiseChunkExtender;

@Mixin(NoiseChunk.class)
public class NoiseChunkExtenderMixin implements NoiseChunkExtender {

  @Unique private ChunkAccessExtender terrasect$chunk;

  @Override
  public void terrasect$setChunk(ChunkAccessExtender chunkAccess) {
    this.terrasect$chunk = chunkAccess;
  }

  @Override
  public ChunkAccessExtender terrasect$getChunk() {
    return this.terrasect$chunk;
  }

  @Inject(method = "cachedClimateSampler", at = @At("RETURN"))
  private void terrasect$setSamplerChunk(
      NoiseRouter noiseRouter,
      List<Climate.ParameterPoint> spawnTarget,
      CallbackInfoReturnable<Climate.Sampler> cir) {
    var sampler = cir.getReturnValue();
    ((ClimateSamplerExtender) (Object) sampler).terrasect$setChunk(this.terrasect$chunk);
  }
}
