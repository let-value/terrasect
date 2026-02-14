package terrasect.mixin;

import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.ChunkAccessExtender;
import terrasect.ClimateSamplerExtender;
import terrasect.generation.Context;
import terrasect.handler.ClimateHandler;

@Mixin(Climate.Sampler.class)
public class ClimateClimateSamplerMixin implements ClimateSamplerExtender {
  @Unique private ChunkAccessExtender terrasect$chunk;

  @Override
  public void terrasect$setChunk(ChunkAccessExtender chunkAccess) {
    this.terrasect$chunk = chunkAccess;
  }

  @Override
  public ChunkAccessExtender terrasect$getChunk() {
    return this.terrasect$chunk;
  }

  @Inject(method = "sample", at = @At("RETURN"))
  private void terrasect$modifyClimate(
      int x, int y, int z, CallbackInfoReturnable<Climate.TargetPoint> cir) {
    if (this.terrasect$chunk == null) {
      return;
    }

    var chunk = this.terrasect$chunk.terrasect$getChunk();

    var self = (Climate.Sampler) (Object) this;

    var context = Context.Companion.get(self);
    if (context == null) {
      return;
    }

    ClimateHandler.INSTANCE.modifyTargetPoint(context, x, z, cir.getReturnValue());
  }
}
