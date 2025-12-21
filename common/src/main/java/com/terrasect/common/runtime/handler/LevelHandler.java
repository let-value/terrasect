package com.terrasect.common.runtime.handler;

import static com.terrasect.common.compat.ResourceKeyCompat.getKeyId;

import com.mojang.datafixers.util.Either;
import com.terrasect.common.Terrasect;
import com.terrasect.common.generation.MinecraftContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

/**
 * Shared level initialization logic for platform mixins.
 * 
 * <p>This class handles registering {@link MinecraftContext} for dimensions
 * that use MultiNoiseBiomeSource.
 */
public final class LevelHandler {
    
    private LevelHandler() {
        // Static utility class
    }
    
    /**
     * Register a MinecraftContext for a dimension.
     * 
     * <p>Call this from LevelMixin after ServerLevel is fully constructed.
     * If biome/climate lookups happen during construction (e.g., spawn finding),
     * they gracefully fall back to vanilla behavior.
     * 
     * @param dimension The dimension resource key
     * @param seed The world seed
     * @param sampler The climate sampler for this dimension
     * @param parameters The biome parameters (either direct or from preset)
     * @return The created MinecraftContext
     */
    public static MinecraftContext registerContext(
            ResourceKey<Level> dimension,
            long seed,
            Climate.Sampler sampler,
            Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters) {
        
        Terrasect.LOGGER.debug("LevelHandler: Registering context for dimension {} with seed {}", 
            getKeyId(dimension), seed);
        
        return MinecraftContext.create(dimension, seed, sampler, parameters);
    }
}
