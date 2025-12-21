package com.terrasect.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.runtime.handler.HeightConstrainedDensityFunction;
import com.terrasect.common.runtime.handler.HeightConstrainedSurfaceLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Self-contained mixin that wraps density functions with height constraints.
 * Uses @Local to capture RandomState from constructor parameters.
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
    
    @WrapOperation(
        method = "<init>",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;preliminarySurfaceLevel()Lnet/minecraft/world/level/levelgen/DensityFunction;")
    )
    private DensityFunction wrapPreliminarySurfaceLevel(NoiseRouter router, Operation<DensityFunction> original,
            @Local(argsOnly = true) RandomState randomState) {
        MinecraftContext ctx = MinecraftContext.get(randomState.sampler());
        return new HeightConstrainedSurfaceLevel(original.call(router), ctx);
    }
}
