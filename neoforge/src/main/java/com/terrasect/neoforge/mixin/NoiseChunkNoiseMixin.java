package com.terrasect.neoforge.mixin;

import com.terrasect.common.compat.NoiseChunkNoiseAccess;
import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.handler.NoiseHandler;

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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Attaches a per-chunk region noise lookup to {@link NoiseChunk}.
 *
 * <p>This mixin is intentionally thin. All logic lives in {@link NoiseHandler}.
 */
@Mixin(NoiseChunk.class)
public class NoiseChunkNoiseMixin implements NoiseChunkNoiseAccess {

    @Unique
    private @Nullable NoiseHandler.NoiseChunkLookup terrasect$noiseLookup;

    @Unique
    private @Nullable NoiseHandler.NoiseChunkLookup terrasect$previousLookupOnInit;

    @Unique
    private @Nullable NoiseHandler.NoiseChunkLookup terrasect$previousLookupOnPreliminarySurface;

    @Inject(method = "<init>", at = @At("HEAD"))
    private void terrasect$beginInitNoiseLookup(
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
        terrasect$noiseLookup = NoiseHandler.buildLookup(ctx, chunkMinX, chunkMinZ);
        terrasect$previousLookupOnInit = NoiseHandler.setActiveLookup(terrasect$noiseLookup);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void terrasect$endInitNoiseLookup(
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
        NoiseHandler.restoreActiveLookup(terrasect$previousLookupOnInit);
        terrasect$previousLookupOnInit = null;
    }

    @Inject(method = "computePreliminarySurfaceLevel", at = @At("HEAD"))
    private void terrasect$beginComputePreliminarySurfaceLevel(long pos, CallbackInfoReturnable<Integer> cir) {
        terrasect$previousLookupOnPreliminarySurface = NoiseHandler.setActiveLookup(terrasect$noiseLookup);
    }

    @Inject(method = "computePreliminarySurfaceLevel", at = @At("RETURN"))
    private void terrasect$endComputePreliminarySurfaceLevel(long pos, CallbackInfoReturnable<Integer> cir) {
        NoiseHandler.restoreActiveLookup(terrasect$previousLookupOnPreliminarySurface);
        terrasect$previousLookupOnPreliminarySurface = null;
    }

    @Override
    public @Nullable NoiseHandler.NoiseChunkLookup terrasect$getNoiseLookup() {
        return terrasect$noiseLookup;
    }

    @Override
    public void terrasect$setNoiseLookup(@Nullable NoiseHandler.NoiseChunkLookup lookup) {
        this.terrasect$noiseLookup = lookup;
    }
}
