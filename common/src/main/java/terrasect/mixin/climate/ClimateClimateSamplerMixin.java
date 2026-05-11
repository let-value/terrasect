package terrasect.mixin.climate;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.biome.Climate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.extender.ClimateSamplerExtender;
import terrasect.extender.NoiseChunkExtender;
import terrasect.handler.ClimateHandler;

@Mixin(Climate.Sampler.class)
public class ClimateClimateSamplerMixin implements ClimateSamplerExtender {
  @Unique
  private static final Logger LOGGER = LoggerFactory.getLogger("Terrasect/ClimateSamplerMixin");

  @Unique private static final AtomicInteger ORIGIN_NULL_CHUNK_LOGS = new AtomicInteger();

  @Unique private NoiseChunkExtender terrasect$noiseChunk;

  @Override
  public void terrasect$setNoiseChunk(NoiseChunkExtender chunkAccess) {
    this.terrasect$noiseChunk = chunkAccess;
  }

  @Override
  public NoiseChunkExtender terrasect$getNoiseChunk() {
    return this.terrasect$noiseChunk;
  }

  @Inject(method = "sample", at = @At("RETURN"))
  private void terrasect$modifyClimate(
      int x, int y, int z, CallbackInfoReturnable<Climate.TargetPoint> cir) {
    if (this.terrasect$noiseChunk == null) {
      terrasect$logOriginSkip(x, y, z, "noiseChunk=NULL");
      return;
    }
    var targetPoint = cir.getReturnValue();
    var chunk = this.terrasect$getNoiseChunk().terrasect$getChunk();
    if (chunk == null) {
      terrasect$logOriginSkip(x, y, z, "chunk=NULL");
      return;
    }
    var context = chunk.terrasect$getContext();
    if (context == null) {
      terrasect$logOriginSkip(x, y, z, "context=NULL");
      return;
    }
    ClimateHandler.INSTANCE.modifyClimate(x, y, z, targetPoint, context);
  }

  @Unique
  private static void terrasect$logOriginSkip(int x, int y, int z, String reason) {
    if (x != 0 || z != 0) {
      return;
    }
    int count = ORIGIN_NULL_CHUNK_LOGS.incrementAndGet();
    if (count <= 3) {
      LOGGER.info(
          "[NC-OriginClimate] sampler skipped #{} quad=({}, {}, {}) reason={}",
          count,
          x,
          y,
          z,
          reason);
    }
  }
}
