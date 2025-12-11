package com.terrasect.common.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegionConstraintsTest {
    @Test
    void rejectsContextOutsideDeclaredConstraints() {
        RegionConstraints constraints = RegionConstraints.builder()
            .allowBiome("minecraft:plains")
            .blockBiome("minecraft:desert")
            .allowStructure("minecraft:village")
            .temperatureRange(-0.2, 0.6)
            .humidityRange(0.0, 1.0)
            .continentalnessRange(-0.5, 0.3)
            .erosionRange(-0.8, 0.2)
            .weirdnessRange(-0.1, 0.4)
            .depthRange(-0.3, 0.2)
            .altitudeRange(64, 100)
            .build();

        GenerationContext allowed = new GenerationContext(
            "minecraft:plains",
            "minecraft:village",
            0.0,
            0.6,
            0.0,
            -0.2,
            0.1,
            -0.1,
            80
        );
        assertTrue(constraints.allows(allowed), "Expected matching biome/structure and ranges to be allowed");

        GenerationContext blockedBiome = allowed.withBiome("minecraft:desert");
        assertFalse(constraints.allows(blockedBiome), "Blocked biome should be rejected");

        GenerationContext missingAllowBiome = allowed.withBiome("minecraft:forest");
        assertFalse(constraints.allows(missingAllowBiome), "Biomes outside allowlist should be rejected");

        GenerationContext disallowedStructure = allowed.withStructure("minecraft:stronghold");
        assertFalse(constraints.allows(disallowedStructure), "Unexpected structure should be rejected when allowlist is set");

        GenerationContext lowAltitude = allowed.withAltitude(40);
        assertFalse(constraints.allows(lowAltitude), "Altitude below minimum should be rejected");

        GenerationContext hotClimate = allowed.withTemperature(0.9);
        assertFalse(constraints.allows(hotClimate), "Temperature above range should be rejected");
    }

    @Test
    void usesBlocklistsWhenAllowlistsAreEmpty() {
        RegionConstraints constraints = RegionConstraints.builder()
            .blockBiome("minecraft:badlands")
            .blockStructure("minecraft:fortress")
            .build();

        GenerationContext neutral = new GenerationContext(
            "minecraft:plains",
            "minecraft:village",
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            64
        );
        assertTrue(constraints.allows(neutral), "Unlisted biome/structure should be allowed by default");

        GenerationContext blocked = neutral.withBiome("minecraft:badlands");
        assertFalse(constraints.allows(blocked), "Blocked biome should be rejected even without allowlist");

        GenerationContext blockedStructure = neutral.withStructure("minecraft:fortress");
        assertFalse(constraints.allows(blockedStructure), "Blocked structure should be rejected even without allowlist");
    }
}
