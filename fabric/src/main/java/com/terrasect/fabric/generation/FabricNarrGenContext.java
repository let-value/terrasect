package com.terrasect.fabric.generation;

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

public class FabricNarrGenContext implements Strategy {
    
    private static final Map<ResourceKey<Level>, FabricNarrGenContext> CONTEXTS = new ConcurrentHashMap<>();
    private static final Map<Climate.Sampler, FabricNarrGenContext> BY_SAMPLER = new ConcurrentHashMap<>();

    private final long seed;
    private final Climate.Sampler sampler;
    private final Either<Climate.ParameterList<Holder<Biome>>, Holder<Biome>> parameters;

    public FabricNarrGenContext(long seed, Climate.Sampler sampler, Either<Climate.ParameterList<Holder<Biome>>, Holder<Biome>> parameters) {
        this.seed = seed;
        this.sampler = sampler;
        this.parameters = parameters;
    }

    public static void register(ResourceKey<Level> dimension, FabricNarrGenContext context) {
        CONTEXTS.put(dimension, context);
        if (context.sampler != null) {
            BY_SAMPLER.put(context.sampler, context);
        }
    }

    public static FabricNarrGenContext get(ResourceKey<Level> dimension) {
        return CONTEXTS.get(dimension);
    }

    public static FabricNarrGenContext get(Climate.Sampler sampler) {
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
        
        return (float) ((target.weirdness() + 1.0) / 2.0);
    }
}
