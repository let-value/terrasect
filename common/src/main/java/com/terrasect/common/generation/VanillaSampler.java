package com.terrasect.common.generation;

import net.minecraft.world.level.biome.Climate;

/**
 * Interface for accessing unmodified vanilla climate sampling.
 * 
 * <p>This interface is implemented via mixin on Climate.Sampler to allow
 * getting the original climate values before any modifications are applied.
 */
public interface VanillaSampler {
    Climate.TargetPoint terrasect$sampleVanilla(int x, int y, int z);
}
