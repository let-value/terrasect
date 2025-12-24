package com.terrasect.common;

import com.terrasect.common.generation.World;

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
     * Get combined river and ridge influence at a location, packed into a single long.
     * This avoids duplicate climate sampling and allocations in hot paths.
     * 
     * <p>Use {@link com.terrasect.common.util.Packer#unpackPairFirst(long)} for river
     * and {@link com.terrasect.common.util.Packer#unpackPairSecond(long)} for ridge.
     * 
     * @param x block x coordinate
     * @param z block z coordinate
     * @return packed influence values (river in lower 32 bits, ridge in upper 32 bits)
     */
    long getInfluence(int x, int z);
    
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
