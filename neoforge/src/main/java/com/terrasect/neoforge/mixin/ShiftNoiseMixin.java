package com.terrasect.neoforge.mixin;

import com.terrasect.common.compat.ShiftNoiseAccess;
import com.terrasect.common.handler.NoiseHandler;

import net.minecraft.world.level.levelgen.DensityFunction;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = {
        "net.minecraft.world.level.levelgen.DensityFunctions$Shift",
        "net.minecraft.world.level.levelgen.DensityFunctions$ShiftA",
        "net.minecraft.world.level.levelgen.DensityFunctions$ShiftB"
})
public class ShiftNoiseMixin implements ShiftNoiseAccess {

    @Shadow
    @Final
    private DensityFunction.NoiseHolder offsetNoise;

    @Override
    public DensityFunction.NoiseHolder terrasect$getOffsetNoise() {
        return offsetNoise;
    }

    @Redirect(
            method = "compute",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/DensityFunctions$ShiftNoise;compute(DDD)D"
            )
    )
    private double terrasect$computeShift(
            @Coerce Object self,
            double x, double y, double z,
            DensityFunction.FunctionContext context) {

        DensityFunction.NoiseHolder noiseHolder = ((ShiftNoiseAccess) self).terrasect$getOffsetNoise();
        return NoiseHandler.sampleNoise(noiseHolder, x * 0.25, y * 0.25, z * 0.25, context) * 4.0;
    }
}
