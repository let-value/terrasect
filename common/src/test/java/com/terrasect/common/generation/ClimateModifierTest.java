package com.terrasect.common.generation;

import com.terrasect.common.ClimateModifier;
import com.terrasect.common.definition.ClimateSettings;

import net.minecraft.world.level.biome.Climate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ClimateModifierTest {
    private static final long DEFAULT_ORIGINAL = 0L;
    private static final long CLIMATE_SCALE = 10000L;

    @Test
    public void noModificationWithNullClimate() {
        Climate.TargetPoint original = new Climate.TargetPoint(
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL);

        Climate.TargetPoint result = ClimateModifier.modify(null, original, 0.0f);

        assertSame(original, result);
        assertEquals(DEFAULT_ORIGINAL, result.temperature());
        assertEquals(DEFAULT_ORIGINAL, result.humidity());
        assertEquals(DEFAULT_ORIGINAL, result.continentalness());
        assertEquals(DEFAULT_ORIGINAL, result.erosion());
        assertEquals(DEFAULT_ORIGINAL, result.depth());
        assertEquals(DEFAULT_ORIGINAL, result.weirdness());
    }

    @Test
    public void noModificationWithEmptyClimate() {
        ClimateSettings empty = ClimateSettings.empty();
        Climate.TargetPoint original = new Climate.TargetPoint(
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL);

        Climate.TargetPoint result = ClimateModifier.modify(empty, original, 0.0f);

        assertSame(original, result);
    }

    @Test
    public void temperatureOnlyModification() {
        ClimateSettings climate = ClimateSettings.builder()
                .temperature(0.8f)
                .build();

        Climate.TargetPoint original = new Climate.TargetPoint(
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL);

        Climate.TargetPoint result = ClimateModifier.modify(climate, original, 0.0f);

        assertEquals(8000L, result.temperature());
        assertEquals(DEFAULT_ORIGINAL, result.humidity());
        assertEquals(DEFAULT_ORIGINAL, result.continentalness());
        assertEquals(DEFAULT_ORIGINAL, result.erosion());
        assertEquals(DEFAULT_ORIGINAL, result.depth());
        assertEquals(DEFAULT_ORIGINAL, result.weirdness());
    }

    @Test
    public void exactValueMapsNeutralToTarget() {
        ClimateSettings climate = ClimateSettings.builder()
                .temperature(0.8f)
                .build();

        Climate.TargetPoint original = new Climate.TargetPoint(
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL);

        Climate.TargetPoint result = ClimateModifier.modify(climate, original, 0.0f);

        assertEquals(8000L, result.temperature());
    }

    @Test
    public void rangeMapsCenterToCenter() {
        ClimateSettings climate = ClimateSettings.builder()
                .temperature(0.0f, 1.0f)
                .build();

        Climate.TargetPoint original = new Climate.TargetPoint(
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL);

        Climate.TargetPoint result = ClimateModifier.modify(climate, original, 0.0f);

        assertEquals(5000L, result.temperature());
    }

    @Test
    public void rangeMapsMinToMin() {
        ClimateSettings climate = ClimateSettings.builder()
                .continentalness(-1.0f, -0.5f)
                .build();

        Climate.TargetPoint original = new Climate.TargetPoint(
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                -CLIMATE_SCALE,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL);

        Climate.TargetPoint result = ClimateModifier.modify(climate, original, 0.0f);

        assertEquals(-CLIMATE_SCALE, result.continentalness());
    }

    @Test
    public void rangeMapsMaxToMax() {
        ClimateSettings climate = ClimateSettings.builder()
                .continentalness(-1.0f, -0.5f)
                .build();

        Climate.TargetPoint original = new Climate.TargetPoint(
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                CLIMATE_SCALE,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL);

        Climate.TargetPoint result = ClimateModifier.modify(climate, original, 0.0f);

        assertEquals(-5000L, result.continentalness());
    }

    @Test
    public void deepOceanRangeForcesNegativeContinentalness() {
        ClimateSettings oceanClimate = ClimateSettings.builder()
                .continentalness(-1.0f, -0.5f)
                .build();

        long inlandOriginal = 8000L;
        Climate.TargetPoint original = new Climate.TargetPoint(
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                inlandOriginal,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL);

        Climate.TargetPoint result = ClimateModifier.modify(oceanClimate, original, 0.0f);

        assertTrue(result.continentalness() < inlandOriginal);
        assertTrue(result.continentalness() >= -CLIMATE_SCALE && result.continentalness() <= -5000L,
                "Result should be in ocean range [-10000, -5000], got: " + result.continentalness());
    }

    @Test
    public void fullRangeProducesNoChange() {
        ClimateSettings fullRange = ClimateSettings.builder()
                .temperature(-1.0f, 1.0f)
                .build();

        Climate.TargetPoint original = new Climate.TargetPoint(
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL);

        Climate.TargetPoint result = ClimateModifier.modify(fullRange, original, 0.0f);

        assertSame(original, result);
        assertEquals(DEFAULT_ORIGINAL, result.temperature());
    }

    @Test
    public void variationPreservedWithinRange() {
        ClimateSettings climate = ClimateSettings.builder()
                .temperature(0.0f, 0.5f)
                .build();

        Climate.TargetPoint low = new Climate.TargetPoint(
                -CLIMATE_SCALE,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL);
        Climate.TargetPoint high = new Climate.TargetPoint(
                CLIMATE_SCALE,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL,
                DEFAULT_ORIGINAL);

        Climate.TargetPoint resultLow = ClimateModifier.modify(climate, low, 0.0f);
        Climate.TargetPoint resultHigh = ClimateModifier.modify(climate, high, 0.0f);

        assertNotEquals(resultLow.temperature(), resultHigh.temperature(), "Variation should be preserved");
        assertEquals(0L, resultLow.temperature());
        assertEquals(5000L, resultHigh.temperature());
    }
}

