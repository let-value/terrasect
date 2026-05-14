package terrasect.mixin.scaffold;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
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
    this.terrasect$chunk = NoiseHandler.getNoiseChunkCreation();
  }

  @Inject(method = "forChunk", at = @At("HEAD"))
  private static void terrasect$beginChunkCreation(
      ChunkAccess chunk,
      RandomState state,
      DensityFunctions.BeardifierOrMarker beardifierOrMarker,
      NoiseGeneratorSettings noiseGeneratorSettings,
      Aquifer.FluidPicker fluidPicke,
      Blender blender,
      CallbackInfoReturnable<NoiseChunk> cir) {
    NoiseHandler.beginNoiseChunkCreation((ChunkAccessExtender) chunk);
  }

  @Inject(method = "forChunk", at = @At("RETURN"))
  private static void terrasect$endChunkCreation(
      ChunkAccess chunk,
      RandomState state,
      DensityFunctions.BeardifierOrMarker beardifierOrMarker,
      NoiseGeneratorSettings noiseGeneratorSettings,
      Aquifer.FluidPicker fluidPicke,
      Blender blender,
      CallbackInfoReturnable<NoiseChunk> cir) {
    NoiseHandler.endNoiseChunkCreation();
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
