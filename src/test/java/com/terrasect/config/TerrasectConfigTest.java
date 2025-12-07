package com.terrasect.config;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TerrasectConfig
 */
class TerrasectConfigTest {
    
    @Test
    void testDefaultBiomeIsPlains() {
        assertEquals("minecraft:plains", TerrasectConfig.getTargetBiomeId());
    }
    
    @Test
    void testSetTargetBiomeId() {
        String originalBiome = TerrasectConfig.getTargetBiomeId();
        try {
            TerrasectConfig.setTargetBiomeId("minecraft:desert");
            assertEquals("minecraft:desert", TerrasectConfig.getTargetBiomeId());
        } finally {
            // Restore original value
            TerrasectConfig.setTargetBiomeId(originalBiome);
        }
    }
    
    @Test
    void testValidBiomeId() {
        assertTrue(TerrasectConfig.isValidBiomeId("minecraft:plains"));
        assertTrue(TerrasectConfig.isValidBiomeId("minecraft:desert"));
        assertTrue(TerrasectConfig.isValidBiomeId("custom:my_biome"));
    }
    
    @Test
    void testInvalidBiomeId() {
        assertFalse(TerrasectConfig.isValidBiomeId("not-a-valid-id"));
        assertFalse(TerrasectConfig.isValidBiomeId(":missing_namespace"));
        assertFalse(TerrasectConfig.isValidBiomeId("missing_path:"));
    }
    
    @Test
    void testGetTargetBiomeReturnsResourceKey() {
        assertNotNull(TerrasectConfig.getTargetBiome());
        assertEquals("minecraft", TerrasectConfig.getTargetBiome().location().getNamespace());
        assertEquals("plains", TerrasectConfig.getTargetBiome().location().getPath());
    }
}
