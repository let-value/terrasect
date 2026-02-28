package terrasect.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.ChunkAccessExtender;
import terrasect.ClimateSamplerExtender;
import terrasect.NoiseChunkExtender;
import terrasect.handler.NoiseHandler;

@Mixin(NoiseChunk.class)
public class NoiseChunkMixin implements NoiseChunkExtender {

  @Shadow final int firstNoiseX = 0;
  @Shadow final int firstNoiseZ = 0;

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
    this.terrasect$chunk = NoiseHandler.pendingChunks.get(ChunkPos.asLong(j >> 4, k >> 4));
  }

  @Override
  public void terrasect$setChunk(ChunkAccessExtender chunkAccess) {
    this.terrasect$chunk = chunkAccess;
  }

  @Override
  public ChunkAccessExtender terrasect$getChunk() {
    return this.terrasect$chunk;
  }

  @WrapOperation(
      method = "<init>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
  private NoiseRouter terrasect$attachChunkToDensityFunctions(
      NoiseRouter router, DensityFunction.Visitor visitor, Operation<NoiseRouter> original) {
    var result = original.call(router, visitor);
    this.terrasect$chunk = NoiseHandler.pendingChunks.get(
        ChunkPos.asLong(this.firstNoiseX >> 2, this.firstNoiseZ >> 2));
    return NoiseHandler.wrapNoiseRouter(result, this);
  }

  @Inject(method = "cachedClimateSampler", at = @At("RETURN"))
  private void terrasect$attachChunkToSampler(
      NoiseRouter noiseRouter,
      List<Climate.ParameterPoint> spawnTarget,
      CallbackInfoReturnable<Climate.Sampler> cir) {
    var sampler = cir.getReturnValue();
    ((ClimateSamplerExtender) (Object) sampler).terrasect$setNoiseChunk(this);
  }
}
