package com.terrasect.common.generation;

import net.minecraft.world.level.biome.Climate;

/**
 * Encapsulates the edge metrics observed from vanilla overworld sampling so we can
 * hardcode them into our region graph without depending on the sampler at runtime.
 * <p>
 * The values below were recorded by running {@code MinecraftEdgeSamplerTest}, which
 * prints multi-scale edge statistics for the overworld noise configuration. They are
 * applied at three different spatial bands:
 * <ul>
 *     <li><strong>Region level</strong> - coarse wobble using long run lengths.</li>
 *     <li><strong>Chunk level</strong> - mid-frequency edge density.</li>
 *     <li><strong>Block level</strong> - tiny jitter to break up straight lines.</li>
 * </ul>
 */
public final class EdgeSpice {

    // Fine-scale biome edge sampling (block / chunk resolution)
    private static final float FINE_TRANSITION_DENSITY = 0.0257f;
    private static final float FINE_HORIZONTAL_JITTER = 1.90f;
    private static final float FINE_VERTICAL_JITTER = 1.78f;
    private static final float FINE_TEMPERATURE_DELTA = 47.3405f;
    private static final float FINE_WEIRDNESS_DELTA = 401.0312f;

    // Macro sampling used to approximate large region borders
    private static final float COARSE_TRANSITION_DENSITY = 0.2366f;
    private static final float COARSE_AVERAGE_RUN_BLOCKS = 130.94f;

    private EdgeSpice() {
    }

    /**
     * Apply vanilla-inspired wobble and climate pushes near region borders so our graph edges
     * feel closer to the measured overworld behavior.
     */
    public static Climate.TargetPoint apply(Climate.TargetPoint base, long seed, int blockX, int blockZ, float edgeDistance) {
        float normalizedEdge = MathUtils.clamp01(edgeDistance / Config.EDGE_SCALE);
        float edgeStrength = 1.0f - normalizedEdge;
        if (edgeStrength <= 0.0f) {
            return base;
        }

        // Region-level wobble: slow undulation along long runs measured in the coarse pass
        float macroWave = (NoiseUtils.valueNoise(blockX, blockZ, seed, 9101, (int) COARSE_AVERAGE_RUN_BLOCKS) - 0.5f)
            * COARSE_TRANSITION_DENSITY * edgeStrength;

        // Chunk-scale density: smaller variations that mirror vanilla transition rates
        float chunkWave = (NoiseUtils.valueNoise(blockX, blockZ, seed, 9102, 32) - 0.5f)
            * FINE_TRANSITION_DENSITY * edgeStrength;

        // Block-level jitter to keep edges lively when stepping through blocks
        float jitterX = (NoiseUtils.valueNoise(blockX, blockZ, seed, 9103, 8) - 0.5f)
            * FINE_HORIZONTAL_JITTER * edgeStrength;
        float jitterZ = (NoiseUtils.valueNoise(blockZ, blockX, seed, 9104, 8) - 0.5f)
            * FINE_VERTICAL_JITTER * edgeStrength;

        // Aggregate the multi-scale waves into climate pushes. The divisors keep the pushes
        // small enough to stay within typical climate parameter ranges while preserving the
        // ratios observed in the sampler output.
        float combinedWave = macroWave + chunkWave;
        double temperature = Climate.unquantizeCoord(base.temperature()) + combinedWave * (FINE_TEMPERATURE_DELTA / 512.0f);
        double humidity = Climate.unquantizeCoord(base.humidity()) + (jitterX + jitterZ) * 0.01f;
        double continentalness = Climate.unquantizeCoord(base.continentalness()) + macroWave * 0.02f;
        double erosion = Climate.unquantizeCoord(base.erosion()) - chunkWave * 0.015f;
        double weirdness = Climate.unquantizeCoord(base.weirdness()) + combinedWave * (FINE_WEIRDNESS_DELTA / 4096.0f);
        double depth = Climate.unquantizeCoord(base.depth());

        return new Climate.TargetPoint(
            Climate.quantizeCoord((float) temperature),
            Climate.quantizeCoord((float) humidity),
            Climate.quantizeCoord((float) continentalness),
            Climate.quantizeCoord((float) erosion),
            Climate.quantizeCoord((float) weirdness),
            Climate.quantizeCoord((float) depth)
        );
    }
}
