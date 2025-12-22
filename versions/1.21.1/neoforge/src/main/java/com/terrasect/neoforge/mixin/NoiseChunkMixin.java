package com.terrasect.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.runtime.handler.HeightConstrainedDensityFunction;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Version-specific mixin for 1.21.1 that wraps density functions with height constraints.
 * 
 * Note: In 1.21.1, NoiseRouter does NOT have a preliminarySurfaceLevel() method.
 * That method was added in 1.21.11+, so we only wrap finalDensity() here.
 */
@Mixin(NoiseChunk.class)
public class NoiseChunkMixin {
    
    @WrapOperation(
        method = "<init>",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;finalDensity()Lnet/minecraft/world/level/levelgen/DensityFunction;")
    )
    private DensityFunction wrapFinalDensity(NoiseRouter router, Operation<DensityFunction> original,
            @Local(argsOnly = true) RandomState randomState) {
        MinecraftContext ctx = MinecraftContext.get(randomState.sampler());
        return new HeightConstrainedDensityFunction(original.call(router), ctx);
    }
}
