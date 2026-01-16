package com.terrasect.fabric.mixin;

import com.terrasect.common.handler.NoiseHandler;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Noise")
public class NoiseMixin {
  @Redirect(
      method = "compute",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/DensityFunction$NoiseHolder;getValue(DDD)D"))
  private double terrasect$sampleNoise(
      DensityFunction.NoiseHolder noiseHolder,
      double x,
      double y,
      double z,
      DensityFunction.FunctionContext context) {
    return NoiseHandler.sampleNoise(noiseHolder, x, y, z, context);
  }
}
