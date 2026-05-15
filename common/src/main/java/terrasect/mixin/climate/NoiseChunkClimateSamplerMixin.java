package terrasect.mixin.climate;

import java.util.List;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.*;
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
  @ModifyVariable(method = "cachedClimateSampler", at = @At("HEAD"), argsOnly = true)
  private NoiseRouter terrasect$wrapClimateRouter(NoiseRouter router) {
    var chunk = ((NoiseChunkExtender) this).terrasect$getChunk();
    ChunkContext context = chunk == null ? null : chunk.terrasect$getContext();
    return NoiseHandler.wrapClimateSamplerRouter(router, context);
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
