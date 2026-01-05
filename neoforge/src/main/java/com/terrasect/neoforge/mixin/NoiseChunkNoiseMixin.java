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
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Attaches a per-chunk region noise lookup to {@link NoiseChunk}.
 *
 * <p>This mixin is intentionally thin. All logic lives in {@link NoiseHandler}.
 */
@Mixin(NoiseChunk.class)
public class NoiseChunkNoiseMixin implements NoiseChunkNoiseAccess {

    @Unique
    private @Nullable NoiseHandler.NoiseChunkLookup terrasect$noiseLookup;

    @Inject(method = "<init>", at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/level/levelgen/NoiseChunk;preliminarySurfaceLevel:Lnet/minecraft/world/level/levelgen/DensityFunction;",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER
    ))
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

        MinecraftContext ctx = MinecraftContext.get(randomState.sampler());
        terrasect$noiseLookup = NoiseHandler.buildLookup(ctx, chunkMinX, chunkMinZ);
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

