package com.terrasect.neoforge.mixin;

import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.lookup.NoiseChunkLookup;
import com.terrasect.common.mixin.NoiseChunkAccessor;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseChunk.class)
public class NoiseChunkNoiseMixin implements NoiseChunkAccessor {

  @Unique private @Nullable NoiseChunkLookup terrasect$noiseLookup;

  @Inject(method = "<init>", at = @At("RETURN"))
  private void terrasect$initNoiseLookup(
      int cellCountXZ,
      RandomState randomState,
      int chunkMinX,
      int chunkMinZ,
      NoiseSettings noiseSettings,
      DensityFunctions.BeardifierOrMarker beardifier,
      NoiseGeneratorSettings generatorSettings,
      Aquifer.FluidPicker fluidPicker,
      Blender blender,
      CallbackInfo ci) {

    var context = MinecraftContext.get(randomState.sampler());
    terrasect$noiseLookup = NoiseChunkLookup.build(context, chunkMinX, chunkMinZ);
  }

  @Override
  public @Nullable NoiseChunkLookup terrasect$getNoiseLookup() {
    return terrasect$noiseLookup;
  }
}
