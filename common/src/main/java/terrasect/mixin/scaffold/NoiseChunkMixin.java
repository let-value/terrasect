package terrasect.mixin.scaffold;

import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import terrasect.extender.ChunkAccessExtender;
import terrasect.extender.NoiseChunkExtender;
import terrasect.handler.NoiseHandler;

@Mixin(NoiseChunk.class)
public class NoiseChunkMixin implements NoiseChunkExtender {

  @Unique private ChunkAccessExtender terrasect$chunk;

  @Inject(method = "<init>", at = @At("CTOR_HEAD"))
  private void terrasect$attachChunk(
      int i,
      RandomState randomState,
      int j,
      int k,
      NoiseSettings noiseSettings,
      DensityFunctions.BeardifierOrMarker beardifierOrMarker,
      NoiseGeneratorSettings noiseGeneratorSettings,
      Aquifer.FluidPicker fluidPicker,
      Blender blender,
      CallbackInfo ci) {
    this.terrasect$chunk = NoiseHandler.pendingChunk.get();
  }

  @Override
  public void terrasect$setChunk(ChunkAccessExtender chunkAccess) {
    this.terrasect$chunk = chunkAccess;
  }

  @Override
  public ChunkAccessExtender terrasect$getChunk() {
    return this.terrasect$chunk;
  }
}
