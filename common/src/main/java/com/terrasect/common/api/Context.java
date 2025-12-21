package com.terrasect.common.api;

import com.terrasect.common.runtime.World;

/**
 * Generation context interface that provides world-specific information
 * needed for narrative region generation.
 * 
 * <p>Implementations are platform-specific (Fabric, NeoForge) and provide
 * access to climate samplers, world seed, and dimension information.
 */
public interface Context {
    
    /**
     * Get the world seed for this generation context.
     */
    long getSeed();
    
    /**
     * Get the river influence at a location (0.0 = no river, 1.0 = river).
     * Used for region boundary adjustments near water features.
     */
    float getRiverInfluence(int x, int z);
    
    /**
     * Get the ridge/weirdness influence at a location.
     * Used for terrain-aware region boundary adjustments.
     */
    float getRidgeInfluence(int x, int z);
    
    /**
     * Get the dimension ID for this generation context.
     * 
     * <p>Returns the Minecraft dimension ResourceLocation as a string,
     * e.g., "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end".
     * 
     * <p>Defaults to Overworld for backwards compatibility.
     * 
     * @return The dimension ID string
     */
    default String getDimensionId() {
        return World.OVERWORLD;
    }
}
