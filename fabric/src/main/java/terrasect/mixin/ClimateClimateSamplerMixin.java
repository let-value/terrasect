package terrasect.mixin;

import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.ClimateSamplerExtender;
import terrasect.NoiseChunkExtender;
import terrasect.handler.ClimateHandler;

@Mixin(Climate.Sampler.class)
public class ClimateClimateSamplerMixin implements ClimateSamplerExtender {
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
      return;
    }
    var targetPoint = cir.getReturnValue();
    var cache = this.terrasect$getNoiseChunk().terrasect$getChunk().terrasect$getCache();
    ClimateHandler.INSTANCE.modifyClimate(x, y, z, targetPoint, cache);
  }
}
