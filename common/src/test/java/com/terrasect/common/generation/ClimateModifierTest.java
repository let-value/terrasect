package com.terrasect.common.generation;

import com.terrasect.common.generation.definition.ClimateSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ClimateModifier class that handles region-based climate calculations.
 */
public class ClimateModifierTest {

    @Test
    public void noModificationWithNullClimate() {
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            null, 0L, 0L, 0.0f
        );
        
        assertEquals(ClimateModifier.ClimateOffset.NONE, offset);
        assertFalse(offset.hasModifications());
    }

    @Test
    public void noModificationWithEmptyClimate() {
        ClimateSettings empty = ClimateSettings.empty();
        
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            empty, 0L, 0L, 0.0f
        );
        
        assertFalse(offset.hasModifications());
        assertNull(offset.temperatureOffset());
        assertNull(offset.humidityOffset());
    }

    @Test
    public void temperatureOnlyModification() {
        ClimateSettings climate = ClimateSettings.builder()
            .temperature(0.8f)
            .build();
        
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            climate, 0L, 0L, 0.0f
        );
        
        assertTrue(offset.hasModifications());
        assertNotNull(offset.temperatureOffset());
        assertNull(offset.humidityOffset());
    }

    @Test
    public void humidityOnlyModification() {
        ClimateSettings climate = ClimateSettings.builder()
            .humidity(0.3f)
            .build();
        
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            climate, 0L, 0L, 0.0f
        );
        
        assertTrue(offset.hasModifications());
        assertNull(offset.temperatureOffset());
        assertNotNull(offset.humidityOffset());
    }

    @Test
    public void bothTemperatureAndHumidityModification() {
        ClimateSettings climate = ClimateSettings.builder()
            .temperature(0.9f)
            .humidity(0.7f)
            .build();
        
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            climate, 0L, 0L, 0.0f
        );
        
        assertTrue(offset.hasModifications());
        assertNotNull(offset.temperatureOffset());
        assertNotNull(offset.humidityOffset());
    }

    @Test
    public void hotClimateIncreasesTemperature() {
        // Target temperature 1.0 (hot) when original is neutral (0)
        ClimateSettings hotClimate = ClimateSettings.builder()
            .temperature(1.0f)
            .build();
        
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            hotClimate, 0L, 0L, 0.0f
        );
        
        assertTrue(offset.temperatureOffset() > 0, 
            "Hot climate should increase temperature from neutral");
    }

    @Test
    public void coldClimateDecreasesTemperature() {
        // Target temperature 0.0 (cold) when original is neutral (0)
        ClimateSettings coldClimate = ClimateSettings.builder()
            .temperature(0.0f)
            .build();
        
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            coldClimate, 0L, 0L, 0.0f
        );
        
        assertTrue(offset.temperatureOffset() < 0, 
            "Cold climate should decrease temperature from neutral");
    }

    @Test
    public void wetClimateIncreasesHumidity() {
        ClimateSettings wetClimate = ClimateSettings.builder()
            .humidity(1.0f)
            .build();
        
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            wetClimate, 0L, 0L, 0.0f
        );
        
        assertTrue(offset.humidityOffset() > 0, 
            "Wet climate should increase humidity from neutral");
    }

    @Test
    public void dryClimateDecreasesHumidity() {
        ClimateSettings dryClimate = ClimateSettings.builder()
            .humidity(0.0f)
            .build();
        
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            dryClimate, 0L, 0L, 0.0f
        );
        
        assertTrue(offset.humidityOffset() < 0, 
            "Dry climate should decrease humidity from neutral");
    }

    @Test
    public void edgeFactorDoesNotReduceInfluenceForHardEdges() {
        ClimateSettings climate = ClimateSettings.builder()
            .temperature(1.0f)
            .build();
        
        // At region center (edgeFactor = 0)
        ClimateModifier.ClimateOffset centerOffset = ClimateModifier.calculateOffset(
            climate, 0L, 0L, 0.0f
        );
        
        // At region edge (edgeFactor = 1)
        ClimateModifier.ClimateOffset edgeOffset = ClimateModifier.calculateOffset(
            climate, 0L, 0L, 1.0f
        );
        
        // With hard edges, edgeFactor should NOT reduce climate influence
        // Both center and edge should have the same full offset
        assertEquals(centerOffset.temperatureOffset(), edgeOffset.temperatureOffset(),
            "Hard edges should apply full climate offset regardless of edge proximity");
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
        ClimateModifier.ClimateOffset offset = new ClimateModifier.ClimateOffset(1000L, null);
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
        ClimateModifier.ClimateOffset offset = new ClimateModifier.ClimateOffset(null, -2000L);
        long original = 3000L;
        
        long result = ClimateModifier.applyHumidityOffset(original, offset);
        
        assertEquals(1000L, result, "Offset should be added to original value");
    }

    @Test
    public void neutralTargetProducesMinimalChange() {
        // Target 0.5 (neutral) should produce minimal offset when original is also neutral
        ClimateSettings neutralClimate = ClimateSettings.builder()
            .temperature(0.5f)
            .humidity(0.5f)
            .build();
        
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            neutralClimate, 0L, 0L, 0.0f
        );
        
        // Should be close to zero (0.5 maps to 0 in Minecraft's scale)
        assertTrue(Math.abs(offset.temperatureOffset()) < 100, 
            "Neutral target with neutral original should produce minimal offset");
        assertTrue(Math.abs(offset.humidityOffset()) < 100, 
            "Neutral target with neutral original should produce minimal offset");
    }

    @Test
    public void extremeValuesProduceLargeOffsets() {
        // Hot and wet (1.0, 1.0) when original is cold and dry (-10000)
        ClimateSettings extremeClimate = ClimateSettings.builder()
            .temperature(1.0f)
            .humidity(1.0f)
            .build();
        
        long coldDry = -10000L;
        
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            extremeClimate, coldDry, coldDry, 0.0f
        );
        
        // Moving from -10000 to +10000 should produce a large positive offset
        assertTrue(offset.temperatureOffset() > 15000, 
            "Extreme change should produce large offset");
        assertTrue(offset.humidityOffset() > 15000, 
            "Extreme change should produce large offset");
    }
}
