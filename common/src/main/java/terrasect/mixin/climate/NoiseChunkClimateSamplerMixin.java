package terrasect.mixin.climate;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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

  @ModifyReturnValue(method = "cachedClimateSampler", at = @At("RETURN"))
  private Climate.Sampler terrasect$attachChunkToSampler(Climate.Sampler sampler) {
    ((ClimateSamplerExtender) (Object) sampler).terrasect$setNoiseChunk((NoiseChunkExtender) this);
    return sampler;
  }
}
