package com.terrasect.fabric.integration;

import com.terrasect.common.integration.ClimateRange;
import net.minecraft.world.level.biome.Climate;

/**
 * Utility methods that translate {@link ClimateRange} envelopes into Minecraft's
 * {@link net.minecraft.world.level.biome.Climate.ParameterPoint} objects for
 * multi-noise biome/region definitions.
 */
public final class MinecraftClimateBridge {
    private MinecraftClimateBridge() {
    }

    public static Climate.ParameterPoint toParameterPoint(
            ClimateRange temperature,
            ClimateRange humidity,
            ClimateRange continentalness,
            ClimateRange erosion,
            ClimateRange depth,
            ClimateRange weirdness,
            ClimateRange offset
    ) {
        return Climate.parameters(
                span(temperature),
                span(humidity),
                span(continentalness),
                span(erosion),
                span(depth),
                span(weirdness),
                midpoint(offset)
        );
    }

    private static Climate.Parameter span(ClimateRange range) {
        return Climate.Parameter.span((float) range.min(), (float) range.max());
    }

    private static float midpoint(ClimateRange range) {
        return (float) ((range.min() + range.max()) / 2.0);
    }
}
