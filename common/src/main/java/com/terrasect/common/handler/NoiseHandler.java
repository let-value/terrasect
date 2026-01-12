package com.terrasect.common.handler;

import com.terrasect.common.mixin.NoiseChunkAccessor;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class NoiseHandler {

    private NoiseHandler() {}

    public static double sampleNoise(
            DensityFunction.NoiseHolder noiseHolder,
            double x,
            double y,
            double z,
            DensityFunction.FunctionContext context) {

        double original = noiseHolder.getValue(x, y, z);

        if (!(context instanceof NoiseChunkAccessor access)) {
            return original;
        }
        var lookup = access.terrasect$getNoiseLookup();
        if (lookup == null) {
            return original;
        }

        int blockX = context.blockX();
        int blockZ = context.blockZ();

        var constraints = lookup.getConstraints(blockX, blockZ);
        if (constraints == null) {
            return original;
        }

        var noiseData = noiseHolder.noiseData();
        if (!(noiseData instanceof Holder.Reference<NormalNoise.NoiseParameters> ref)) {
            return original;
        }

        var key = ref.key();
        var transform = constraints.findNoiseTransform(key);
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
