package com.terrasect.common.generation;

import com.terrasect.common.generation.definition.ClimateSettings;
import com.terrasect.common.generation.definition.ClimateSettings.ClimateRange;
import com.terrasect.common.runtime.ClimateModifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ClimateModifier class that handles region-based climate calculations.
 * 
 * <p>Climate values are mapped from the original [-1, 1] range into a target range.
 * The original value is normalized to [0, 1] then mapped into [min, max].
 */
public class ClimateModifierTest {

    // Default original values for parameters we're not testing
    private static final long DEFAULT_ORIGINAL = 0L;
    private static final long CLIMATE_SCALE = 10000L;

    @Test
    public void noModificationWithNullClimate() {
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            null, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL,
            DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, 0.0f
        );
        
        assertEquals(ClimateModifier.ClimateOffset.NONE, offset);
        assertFalse(offset.hasModifications());
    }

    @Test
    public void noModificationWithEmptyClimate() {
        ClimateSettings empty = ClimateSettings.empty();
        
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            empty, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL,
            DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, 0.0f
        );
        
        assertFalse(offset.hasModifications());
        assertNull(offset.temperatureOffset());
        assertNull(offset.humidityOffset());
        assertNull(offset.continentalnessOffset());
        assertNull(offset.erosionOffset());
        assertNull(offset.depthOffset());
        assertNull(offset.weirdnessOffset());
    }

    @Test
    public void temperatureOnlyModification() {
        // Using exact value sets min=max=0.8
        ClimateSettings climate = ClimateSettings.builder()
            .temperature(0.8f)
            .build();
        
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            climate, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL,
            DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, 0.0f
        );
        
        assertTrue(offset.hasModifications());
        assertNotNull(offset.temperatureOffset());
        assertNull(offset.humidityOffset());
    }

    @Test
    public void exactValueMapsNeutralToTarget() {
        // Exact value 0.8: all originals map to 0.8
        ClimateSettings climate = ClimateSettings.builder()
            .temperature(0.8f)
            .build();
        
        // Original 0 (neutral) should map to 0.8 (8000 quantized)
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            climate, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL,
            DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, 0.0f
        );
        
        // Target is 8000, original is 0, so offset should be 8000
        assertEquals(8000L, offset.temperatureOffset());
    }

    @Test
    public void rangeMapsCenterToCenter() {
        // Range [0.0, 1.0]: original 0 (center) maps to 0.5 (center of range)
        ClimateSettings climate = ClimateSettings.builder()
            .temperature(0.0f, 1.0f)
            .build();
        
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            climate, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL,
            DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, 0.0f
        );
        
        // Original 0 normalizes to 0.5, maps to 0.5 in [0,1] range = 5000
        // Offset = 5000 - 0 = 5000
        assertEquals(5000L, offset.temperatureOffset());
    }

    @Test
    public void rangeMapsMinToMin() {
        // Range [-1.0, -0.5]: original -10000 maps to -10000 (min of range)
        ClimateSettings climate = ClimateSettings.builder()
            .continentalness(-1.0f, -0.5f)
            .build();
        
        long originalMin = -CLIMATE_SCALE;
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            climate, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, originalMin,
            DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, 0.0f
        );
        
        // Original -10000 normalizes to 0, maps to -1.0 in range = -10000
        // Offset = -10000 - (-10000) = 0
        assertEquals(0L, offset.continentalnessOffset());
    }

    @Test
    public void rangeMapsMaxToMax() {
        // Range [-1.0, -0.5]: original +10000 maps to -5000 (max of range)
        ClimateSettings climate = ClimateSettings.builder()
            .continentalness(-1.0f, -0.5f)
            .build();
        
        long originalMax = CLIMATE_SCALE;
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            climate, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, originalMax,
            DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, 0.0f
        );
        
        // Original 10000 normalizes to 1.0, maps to -0.5 in range = -5000
        // Offset = -5000 - 10000 = -15000
        assertEquals(-15000L, offset.continentalnessOffset());
    }

    @Test
    public void deepOceanRangeForcesNegativeContinentalness() {
        // Deep ocean range [-1.0, -0.5]
        ClimateSettings oceanClimate = ClimateSettings.builder()
            .continentalness(-1.0f, -0.5f)
            .build();
        
        // Test with inland original value (positive)
        long inlandOriginal = 8000L; // 0.8 on scale
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            oceanClimate, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, inlandOriginal,
            DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, 0.0f
        );
        
        // Should have negative offset to move into ocean range
        assertTrue(offset.continentalnessOffset() < 0,
            "Ocean range should produce negative offset from inland terrain");
        
        // Result should be in ocean range
        long result = inlandOriginal + offset.continentalnessOffset();
        assertTrue(result >= -CLIMATE_SCALE && result <= -5000L,
            "Result should be in ocean range [-10000, -5000], got: " + result);
    }

    @Test
    public void applyTemperatureOffsetWithNull() {
        ClimateModifier.ClimateOffset offset = ClimateModifier.ClimateOffset.NONE;
        long original = 5000L;
        
        long result = ClimateModifier.applyTemperatureOffset(original, offset);
        
        assertEquals(original, result, "Null offset should not change original value");
    }

    @Test
    public void applyTemperatureOffsetWithValue() {
        ClimateModifier.ClimateOffset offset = new ClimateModifier.ClimateOffset(
            1000L, null, null, null, null, null);
        long original = 5000L;
        
        long result = ClimateModifier.applyTemperatureOffset(original, offset);
        
        assertEquals(6000L, result, "Offset should be added to original value");
    }

    @Test
    public void applyHumidityOffsetWithNull() {
        ClimateModifier.ClimateOffset offset = ClimateModifier.ClimateOffset.NONE;
        long original = 3000L;
        
        long result = ClimateModifier.applyHumidityOffset(original, offset);
        
        assertEquals(original, result, "Null offset should not change original value");
    }

    @Test
    public void applyHumidityOffsetWithValue() {
        ClimateModifier.ClimateOffset offset = new ClimateModifier.ClimateOffset(
            null, -2000L, null, null, null, null);
        long original = 3000L;
        
        long result = ClimateModifier.applyHumidityOffset(original, offset);
        
        assertEquals(1000L, result, "Offset should be added to original value");
    }

    @Test
    public void allSixParametersWithRanges() {
        ClimateSettings fullClimate = ClimateSettings.builder()
            .temperature(-1.0f, 0.0f)   // cold range
            .humidity(0.5f, 1.0f)       // wet range
            .continentalness(-1.0f, -0.5f) // ocean range
            .erosion(0.0f, 1.0f)        // flat range
            .depth(-0.5f, 0.5f)         // mid depth
            .weirdness(-1.0f, 1.0f)     // full range (no change)
            .build();
        
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            fullClimate, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL,
            DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, 0.0f
        );
        
        assertTrue(offset.hasModifications());
        assertNotNull(offset.temperatureOffset());
        assertNotNull(offset.humidityOffset());
        assertNotNull(offset.continentalnessOffset());
        assertNotNull(offset.erosionOffset());
        assertNotNull(offset.depthOffset());
        assertNotNull(offset.weirdnessOffset());
    }

    @Test
    public void fullRangeProducesNoOffset() {
        // Full range [-1.0, 1.0] should produce no meaningful change
        ClimateSettings fullRange = ClimateSettings.builder()
            .temperature(-1.0f, 1.0f)
            .build();
        
        // Original 0 (center) maps to 0 (center of full range)
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            fullRange, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL,
            DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, 0.0f
        );
        
        // Offset should be 0 since range matches original range
        assertEquals(0L, offset.temperatureOffset(),
            "Full range should produce zero offset at center");
    }

    @Test
    public void applyErosionOffset() {
        ClimateModifier.ClimateOffset offset = new ClimateModifier.ClimateOffset(
            null, null, null, 3000L, null, null);
        long original = 2000L;
        
        long result = ClimateModifier.applyErosionOffset(original, offset);
        
        assertEquals(5000L, result, "Erosion offset should be added to original value");
    }

    @Test
    public void applyDepthOffset() {
        ClimateModifier.ClimateOffset offset = new ClimateModifier.ClimateOffset(
            null, null, null, null, -1000L, null);
        long original = 5000L;
        
        long result = ClimateModifier.applyDepthOffset(original, offset);
        
        assertEquals(4000L, result, "Depth offset should be added to original value");
    }

    @Test
    public void applyWeirdnessOffset() {
        ClimateModifier.ClimateOffset offset = new ClimateModifier.ClimateOffset(
            null, null, null, null, null, 2500L);
        long original = 1000L;
        
        long result = ClimateModifier.applyWeirdnessOffset(original, offset);
        
        assertEquals(3500L, result, "Weirdness offset should be added to original value");
    }
    
    @Test
    public void variationPreservedWithinRange() {
        // Range [0.0, 0.5]: test that different originals map to different targets
        ClimateSettings climate = ClimateSettings.builder()
            .temperature(0.0f, 0.5f)
            .build();
        
        long originalLow = -CLIMATE_SCALE;  // -1.0
        long originalHigh = CLIMATE_SCALE;   // +1.0
        
        ClimateModifier.ClimateOffset offsetLow = ClimateModifier.calculateOffset(
            climate, originalLow, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL,
            DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, 0.0f
        );
        
        ClimateModifier.ClimateOffset offsetHigh = ClimateModifier.calculateOffset(
            climate, originalHigh, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL,
            DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, DEFAULT_ORIGINAL, 0.0f
        );
        
        long resultLow = originalLow + offsetLow.temperatureOffset();
        long resultHigh = originalHigh + offsetHigh.temperatureOffset();
        
        // Results should be different (variation preserved)
        assertNotEquals(resultLow, resultHigh, "Variation should be preserved");
        
        // Results should be in range [0, 5000]
        assertEquals(0L, resultLow, "Low original should map to range min");
        assertEquals(5000L, resultHigh, "High original should map to range max");
    }
}
