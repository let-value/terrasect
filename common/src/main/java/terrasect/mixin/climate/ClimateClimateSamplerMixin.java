package terrasect.mixin.climate;

import net.minecraft.world.level.biome.Climate;
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
      ClimateHandler.traceSamplerSkip(x, y, z, "noiseChunk=NULL");
      return;
    }
    var chunk = this.terrasect$noiseChunk.terrasect$getChunk();
    if (chunk == null) {
      ClimateHandler.traceSamplerSkip(x, y, z, "chunk=NULL");
      return;
    }
    var context = chunk.terrasect$getContext();
    if (context == null) {
      ClimateHandler.traceSamplerSkip(x, y, z, "context=NULL");
      return;
    }
    ClimateHandler.INSTANCE.modifyClimate(x, y, z, cir.getReturnValue(), context);
  }
}
