package terrasect.mixin.climate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.extender.ClimateSamplerExtender;
import terrasect.extender.NoiseChunkExtender;
import terrasect.generation.ChunkContext;
import terrasect.handler.NoiseHandler;

@Mixin(NoiseChunk.class)
public class NoiseChunkClimateSamplerMixin {
  private static final Logger LOGGER =
      LoggerFactory.getLogger("Terrasect/NoiseChunkClimateSamplerMixin");
  private static final AtomicInteger CLIMATE_ROUTER_WRAP_LOGS = new AtomicInteger();

  @ModifyVariable(method = "cachedClimateSampler", at = @At("HEAD"), argsOnly = true)
  private NoiseRouter terrasect$wrapClimateRouter(NoiseRouter router) {
    var chunkExtender = ((NoiseChunkExtender) this).terrasect$getChunk();
    if (chunkExtender == null) {
      LOGGER.warn(
          "[NC-ClimateRouter] cachedClimateSampler has no chunk; climate noise constraints skipped");
      return router;
    }

    ChunkContext context = chunkExtender.terrasect$getContext();
    if (context == null) {
      LOGGER.warn(
          "[NC-ClimateRouter] cachedClimateSampler has no ChunkContext; climate noise constraints skipped");
      return router;
    }

    var count = CLIMATE_ROUTER_WRAP_LOGS.incrementAndGet();
    if (count <= 8 || count % 500 == 0) {
      LOGGER.info("[NC-ClimateRouter] wrapping climate sampler router #{}", count);
    }
    return NoiseHandler.wrapNoiseRouter(router, context);
  }

  @Inject(method = "cachedClimateSampler", at = @At("RETURN"))
  private void terrasect$attachChunkToSampler(
      NoiseRouter noiseRouter,
      List<Climate.ParameterPoint> spawnTarget,
      CallbackInfoReturnable<Climate.Sampler> cir) {
    var sampler = cir.getReturnValue();
    ((ClimateSamplerExtender) (Object) sampler).terrasect$setNoiseChunk((NoiseChunkExtender) this);
  }
}
