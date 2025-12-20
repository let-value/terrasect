package com.terrasect.fabric.generation;

import net.minecraft.world.level.biome.Climate;

/**
 * Interface injected into Climate.Sampler via mixin to provide
 * access to vanilla (unmodified) climate sampling.
 * 
 * <p>This allows getRiverInfluence/getRidgeInfluence to get original
 * climate values without triggering our ClimateSamplerMixin modifications.
 */
public interface VanillaSampler {
    
    /**
     * Sample climate values using vanilla logic, bypassing any mixin modifications.
     * 
     * @param x Quart X coordinate
     * @param y Quart Y coordinate  
     * @param z Quart Z coordinate
     * @return The unmodified climate target point
     */
    Climate.TargetPoint terrasect$sampleVanilla(int x, int y, int z);
}
