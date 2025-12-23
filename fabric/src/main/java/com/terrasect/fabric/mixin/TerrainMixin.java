package com.terrasect.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.runtime.handler.HeightConstrainedDensityFunction;
import com.terrasect.common.runtime.handler.HeightConstrainedSurfaceLevel;
import com.terrasect.common.runtime.handler.TerrainHeightLookup;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that wraps density functions with height constraints.
 * 
 * <p>Uses {@link TerrainHeightLookup} to pre-calculate max heights for the entire chunk
 * once during NoiseChunk construction, avoiding repeated region lookups during terrain generation.
 */
@Mixin(NoiseChunk.class)
public class TerrainMixin {
    
    /** Pre-computed height lookup for this chunk */
    @Unique
    private TerrainHeightLookup terrasect$heightLookup;
    
    /**
     * Build TerrainHeightLookup at the end of NoiseChunk construction.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void buildHeightLookup(
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
        MinecraftContext ctx = MinecraftContext.get(randomState.sampler());
        terrasect$heightLookup = TerrainHeightLookup.build(ctx, chunkMinX, chunkMinZ);
    }
    
    @WrapOperation(
        method = "<init>",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;finalDensity()Lnet/minecraft/world/level/levelgen/DensityFunction;")
    )
    private DensityFunction wrapFinalDensity(
            NoiseRouter router,
            Operation<DensityFunction> original,
            @Local(argsOnly = true) RandomState randomState,
            @Local(argsOnly = true, ordinal = 1) int chunkMinX,
            @Local(argsOnly = true, ordinal = 2) int chunkMinZ) {
        // Lookup not yet built at this point - build inline
        if (terrasect$heightLookup == null) {
            MinecraftContext ctx = MinecraftContext.get(randomState.sampler());
            terrasect$heightLookup = TerrainHeightLookup.build(ctx, chunkMinX, chunkMinZ);
        }
        return new HeightConstrainedDensityFunction(original.call(router), terrasect$heightLookup);
    }
    
    @WrapOperation(
        method = "<init>",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;preliminarySurfaceLevel()Lnet/minecraft/world/level/levelgen/DensityFunction;")
    )
    private DensityFunction wrapPreliminarySurfaceLevel(
            NoiseRouter router,
            Operation<DensityFunction> original,
            @Local(argsOnly = true) RandomState randomState,
            @Local(argsOnly = true, ordinal = 1) int chunkMinX,
            @Local(argsOnly = true, ordinal = 2) int chunkMinZ) {
        // Lookup not yet built at this point - build inline
        if (terrasect$heightLookup == null) {
            MinecraftContext ctx = MinecraftContext.get(randomState.sampler());
            terrasect$heightLookup = TerrainHeightLookup.build(ctx, chunkMinX, chunkMinZ);
        }
        return new HeightConstrainedSurfaceLevel(original.call(router), terrasect$heightLookup);
    }
}
