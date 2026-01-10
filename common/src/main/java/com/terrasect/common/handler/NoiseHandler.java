package com.terrasect.common.handler;

import com.terrasect.common.compat.NoiseChunkNoiseAccess;
import com.terrasect.common.lookup.CompiledNoiseRegistry;
import com.terrasect.common.lookup.NoiseChunkLookup;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Shared noise modification logic for platform mixins.
 *
 * <p>Hot paths must not allocate. Region lookups are built once per {@code NoiseChunk} and then
 * reused by redirects on vanilla density-function noise sampling.
 */
public final class NoiseHandler {

    private NoiseHandler() {}

    /**
     * Sample a vanilla {@link DensityFunction.NoiseHolder} value and apply region constraints.
     *
     * <p>This must not allocate. The context must implement {@link NoiseChunkNoiseAccess} to provide
     * the lookup; otherwise the original noise value is returned unchanged.
     */
    public static double sampleNoise(
            DensityFunction.NoiseHolder noiseHolder,
            double x,
            double y,
            double z,
            DensityFunction.FunctionContext context) {

        double original = noiseHolder.getValue(x, y, z);

        if (!(context instanceof NoiseChunkNoiseAccess access)) {
            return original;
        }
        NoiseChunkLookup lookup = access.terrasect$getNoiseLookup();
        if (lookup == null) {
            return original;
        }

        int blockX = context.blockX();
        int blockZ = context.blockZ();

        CompiledNoiseRegistry.CompiledNoiseConstraints constraints = lookup.getConstraints(blockX, blockZ);
        if (constraints == null) {
            return original;
        }

        Holder<NormalNoise.NoiseParameters> noiseData = noiseHolder.noiseData();
        if (!(noiseData instanceof Holder.Reference<NormalNoise.NoiseParameters> ref)) {
            return original;
        }

        ResourceKey<NormalNoise.NoiseParameters> key = ref.key();
        CompiledNoiseRegistry.CompiledTransform transform = constraints.findNoiseTransform(key);
        if (transform == null) {
            return original;
        }

        float strength = lookup.getStrength(blockX, blockZ);
        if (strength <= 0.0f) {
            return original;
        }

        double transformed = transform.apply(original);
        if (strength >= 1.0f) {
            return transformed;
        }

        return original + (transformed - original) * strength;
    }
}
