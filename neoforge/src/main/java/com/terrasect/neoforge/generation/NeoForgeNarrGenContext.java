package com.terrasect.neoforge.generation;

import com.mojang.datafixers.util.Either;
import com.terrasect.common.generation.Strategy;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NeoForge implementation of the Strategy interface for narrative generation context.
 * Mirrors the Fabric implementation to provide the same functionality.
 */
public class NeoForgeNarrGenContext implements Strategy {
    
    private static final Map<ResourceKey<Level>, NeoForgeNarrGenContext> CONTEXTS = new ConcurrentHashMap<>();
    private static final Map<Climate.Sampler, NeoForgeNarrGenContext> BY_SAMPLER = new ConcurrentHashMap<>();

    private final long seed;
    private final Climate.Sampler sampler;
    private final Either<Climate.ParameterList<Holder<Biome>>, Holder<Biome>> parameters;

    public NeoForgeNarrGenContext(long seed, Climate.Sampler sampler, Either<Climate.ParameterList<Holder<Biome>>, Holder<Biome>> parameters) {
        this.seed = seed;
        this.sampler = sampler;
        this.parameters = parameters;
    }

    public static void register(ResourceKey<Level> dimension, NeoForgeNarrGenContext context) {
        CONTEXTS.put(dimension, context);
        if (context.sampler != null) {
            BY_SAMPLER.put(context.sampler, context);
        }
    }

    public static NeoForgeNarrGenContext get(ResourceKey<Level> dimension) {
        return CONTEXTS.get(dimension);
    }

    public static NeoForgeNarrGenContext get(Climate.Sampler sampler) {
        return BY_SAMPLER.get(sampler);
    }

    public static void clear() {
        CONTEXTS.clear();
        BY_SAMPLER.clear();
    }

    @Override
    public long getSeed() {
        return seed;
    }

    @Override
    public float getRiverInfluence(int x, int z) {
        if (sampler == null || parameters == null) return 0.0f;
        
        // Convert block coords to quart coords (approximate Y=sea level)
        int qx = x >> 2;
        int qy = 16; 
        int qz = z >> 2;
        
        Climate.TargetPoint target = sampler.sample(qx, qy, qz);
        
        Holder<Biome> biome = parameters.map(
            list -> list.findValue(target),
            b -> b
        );
        
        return biome.is(BiomeTags.IS_RIVER) ? 1.0f : 0.0f;
    }

    @Override
    public float getRidgeInfluence(int x, int z) {
        if (sampler == null) return 0.0f;
        
        int qx = x >> 2;
        int qy = 16; 
        int qz = z >> 2;
        Climate.TargetPoint target = sampler.sample(qx, qy, qz);
        
        // target.weirdness() returns a quantized long in range approximately [-10000, 10000]
        // Normalize to [0, 1] range for ridge influence
        long weirdness = target.weirdness();
        // Clamp to expected range and normalize
        float normalized = (weirdness + 10000.0f) / 20000.0f;
        return Math.max(0.0f, Math.min(1.0f, normalized));
    }
}
