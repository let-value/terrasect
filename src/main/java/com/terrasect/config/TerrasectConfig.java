package com.terrasect.config;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.core.registries.Registries;

/**
 * Configuration for the Terrasect mod
 * This allows specifying which biome should be used for the entire world
 * 
 * To change the biome, modify the targetBiomeId field below.
 * 
 * Popular biome options:
 * - "minecraft:plains" (default)
 * - "minecraft:desert"
 * - "minecraft:jungle"
 * - "minecraft:ice_spikes"
 * - "minecraft:mushroom_fields"
 * - "minecraft:cherry_grove"
 * - "minecraft:badlands"
 * - "minecraft:deep_dark"
 * 
 * See the full list at: https://minecraft.wiki/w/Biome#Biome_IDs
 */
public class TerrasectConfig {
    
    // Default biome to use - change this to any valid biome ID
    private static String targetBiomeId = "minecraft:plains";
    
    /**
     * Get the target biome ID (e.g., "minecraft:plains")
     */
    public static String getTargetBiomeId() {
        return targetBiomeId;
    }
    
    /**
     * Set the target biome ID
     */
    public static void setTargetBiomeId(String biomeId) {
        targetBiomeId = biomeId;
    }
    
    /**
     * Get the target biome as a ResourceKey
     */
    public static ResourceKey<Biome> getTargetBiome() {
        ResourceLocation location = ResourceLocation.parse(targetBiomeId);
        return ResourceKey.create(Registries.BIOME, location);
    }
    
    /**
     * Check if the configured biome ID is valid
     */
    public static boolean isValidBiomeId(String biomeId) {
        try {
            ResourceLocation.parse(biomeId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
