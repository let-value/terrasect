package com.terrasect.fabric.mixin;

import com.terrasect.common.mixin.ShiftNoiseAccess;
import com.terrasect.common.handler.NoiseHandler;

import net.minecraft.world.level.levelgen.DensityFunction;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Shift")
public class ShiftNoiseMixin implements ShiftNoiseAccess {

    @Shadow
    @Final
    private DensityFunction.NoiseHolder offsetNoise;

    @Override
    public DensityFunction.NoiseHolder terrasect$getOffsetNoise() {
        return offsetNoise;
    }

    @Inject(method = "compute(Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)D",
            at = @At("HEAD"), cancellable = true)
    private void terrasect$computeShift(DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
        double x = context.blockX();
        double y = context.blockY();
        double z = context.blockZ();
        double result = NoiseHandler.sampleNoise(offsetNoise, x * 0.25, y * 0.25, z * 0.25, context) * 4.0;
        cir.setReturnValue(result);
    }
}
