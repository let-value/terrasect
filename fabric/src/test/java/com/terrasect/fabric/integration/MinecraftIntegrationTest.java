package com.terrasect.fabric.integration;

import com.terrasect.common.integration.ClimateRange;
import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MinecraftIntegrationTest {
    @Test
    void regionConstraintsClampIntoMinecraftParameterPoint() {
        ClimateRange temperature = new ClimateRange(-0.2, 0.3);
        ClimateRange humidity = new ClimateRange(0.1, 0.9);
        ClimateRange continentalness = new ClimateRange(-0.4, 0.4);
        ClimateRange erosion = new ClimateRange(-0.25, 0.25);
        ClimateRange depth = new ClimateRange(0.0, 1.0);
        ClimateRange weirdness = new ClimateRange(-0.5, 0.5);
        ClimateRange offset = new ClimateRange(0.0, 0.1);

        Climate.ParameterPoint point = MinecraftClimateBridge.toParameterPoint(
                temperature,
                humidity,
                continentalness,
                erosion,
                depth,
                weirdness,
                offset
        );

        float scaledTemperatureMin = (float) (temperature.min() * 10_000.0);
        float scaledTemperatureMax = (float) (temperature.max() * 10_000.0);
        float scaledErosionMin = (float) (erosion.min() * 10_000.0);
        float scaledErosionMax = (float) (erosion.max() * 10_000.0);

        assertEquals(scaledTemperatureMin, point.temperature().min(), 1e-2);
        assertEquals(scaledTemperatureMax, point.temperature().max(), 1e-2);
        assertEquals(scaledErosionMin, point.erosion().min(), 1e-2);
        assertEquals(scaledErosionMax, point.erosion().max(), 1e-2);
    }
}
