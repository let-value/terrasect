package terrasect.mixin.climate;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
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

  @ModifyReturnValue(method = "sample", at = @At("RETURN"))
  private Climate.TargetPoint terrasect$modifyClimate(
      Climate.TargetPoint targetPoint, int x, int y, int z) {
    ClimateHandler.INSTANCE.modifyClimate(x, y, z, targetPoint, this.terrasect$noiseChunk);
    return targetPoint;
  }
}
