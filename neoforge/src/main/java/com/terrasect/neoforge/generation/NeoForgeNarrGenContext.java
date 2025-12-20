package com.terrasect.neoforge.generation;

import com.mojang.datafixers.util.Either;
import com.terrasect.common.api.Context;
import com.terrasect.common.lookup.BiomeLookup;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NeoForge implementation of the Context interface.
 * 
 * <p>This class provides:
 * <ul>
 *   <li>Dimension-specific context (ID, seed, sampler)</li>
 *   <li>Pre-built {@link BiomeLookup} for O(1) biome filtering</li>
 *   <li>Helper methods for extracting biome metadata from MC types</li>
 * </ul>
 */
public class NeoForgeNarrGenContext implements Context {
    
    private static final Map<ResourceKey<Level>, NeoForgeNarrGenContext> CONTEXTS = new ConcurrentHashMap<>();
    private static final Map<Climate.Sampler, NeoForgeNarrGenContext> BY_SAMPLER = new ConcurrentHashMap<>();

    private final long seed;
    private final Climate.Sampler sampler;
    private final Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters;
    private final String dimensionId;
    private final BiomeLookup<Holder<Biome>> biomeLookup;

    public NeoForgeNarrGenContext(long seed, Climate.Sampler sampler, 
            Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters) {
        this(seed, sampler, parameters, "minecraft:overworld");
    }
    
    public NeoForgeNarrGenContext(long seed, Climate.Sampler sampler, 
            Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters, 
            String dimensionId) {
        this.seed = seed;
        this.sampler = sampler;
        this.parameters = parameters;
        this.dimensionId = dimensionId;
        this.biomeLookup = buildBiomeLookup(parameters);
    }
    
    // ========== Lookup Pre-baking ==========
    
    /**
     * Build the biome metadata lookup for O(1) filtering.
     * Called once during context construction.
     */
    private static BiomeLookup<Holder<Biome>> buildBiomeLookup(
            Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters) {
        if (parameters == null) {
            return BiomeLookup.<Holder<Biome>>builder().build();
        }
        
        Climate.ParameterList<Holder<Biome>> paramList = parameters.map(
            list -> list,
            holder -> holder.value().parameters()
        );
        
        BiomeLookup.Builder<Holder<Biome>> builder = BiomeLookup.builder();
        Set<Holder<Biome>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        
        for (var entry : paramList.values()) {
            Holder<Biome> biome = entry.getSecond();
            if (seen.add(biome)) {
                builder.add(biome, getBiomeId(biome), getBiomeTags(biome));
            }
        }
        
        return builder.build();
    }
    
    // ========== MC Type Helpers ==========
    
    /**
     * Extract the biome ID from a holder.
     */
    public static String getBiomeId(Holder<Biome> biome) {
        return biome.unwrapKey()
            .map(key -> key.identifier().toString())
            .orElse("unknown");
    }
    
    /**
     * Extract the biome tags from a holder.
     */
    public static Set<String> getBiomeTags(Holder<Biome> biome) {
        Set<String> tags = new HashSet<>();
        biome.tags().forEach(tag -> tags.add("#" + tag.location().toString()));
        return tags;
    }
    
    // ========== Registry ==========
    
    /**
     * Create a context from a Minecraft ResourceKey dimension.
     */
    public static NeoForgeNarrGenContext create(ResourceKey<Level> dimension, long seed, Climate.Sampler sampler, 
            Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters) {
        return new NeoForgeNarrGenContext(seed, sampler, parameters, toDimensionId(dimension));
    }
    
    private static String toDimensionId(ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) {
            return "minecraft:overworld";
        } else if (dimension == Level.NETHER) {
            return "minecraft:the_nether";
        } else if (dimension == Level.END) {
            return "minecraft:the_end";
        }
        // For modded dimensions, parse from ResourceKey string
        String str = dimension.toString();
        int slashIdx = str.indexOf(" / ");
        if (slashIdx > 0) {
            int endIdx = str.indexOf(']', slashIdx);
            if (endIdx > slashIdx) {
                return str.substring(slashIdx + 3, endIdx);
            }
        }
        return "minecraft:overworld";
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
        NeoForgeNarrGenContext ctx = BY_SAMPLER.get(sampler);
        if (ctx != null) {
            return ctx;
        }
        // Fallback chain: Overworld -> any registered context -> null
        ctx = CONTEXTS.get(Level.OVERWORLD);
        if (ctx != null) {
            return ctx;
        }
        return CONTEXTS.values().stream().findFirst().orElse(null);
    }

    public static void clear() {
        CONTEXTS.clear();
        BY_SAMPLER.clear();
    }

    // ========== Context Interface ==========

    @Override
    public long getSeed() {
        return seed;
    }
    
    @Override
    public String getDimensionId() {
        return dimensionId;
    }
    
    public BiomeLookup<Holder<Biome>> getBiomeLookup() {
        return biomeLookup;
    }

    @Override
    public float getRiverInfluence(int x, int z) {
        if (sampler == null || parameters == null) return 0.0f;
        
        int qx = x >> 2;
        int qy = 16; 
        int qz = z >> 2;
        
        // Set flag to bypass our mixin, get vanilla sample, then clear flag
        Climate.TargetPoint target;
        SamplerBypass.setWantVanilla(true);
        try {
            target = sampler.sample(qx, qy, qz);
        } finally {
            SamplerBypass.setWantVanilla(false);
        }
        
        Climate.ParameterList<Holder<Biome>> paramList = parameters.map(
            list -> list,
            holder -> holder.value().parameters()
        );
        
        Holder<Biome> biome = paramList.findValue(target);
        return biome.is(BiomeTags.IS_RIVER) ? 1.0f : 0.0f;
    }

    @Override
    public float getRidgeInfluence(int x, int z) {
        if (sampler == null) return 0.0f;
        
        int qx = x >> 2;
        int qy = 16; 
        int qz = z >> 2;
        
        // Set flag to bypass our mixin, get vanilla sample, then clear flag
        Climate.TargetPoint target;
        SamplerBypass.setWantVanilla(true);
        try {
            target = sampler.sample(qx, qy, qz);
        } finally {
            SamplerBypass.setWantVanilla(false);
        }
        
        long weirdness = target.weirdness();
        float normalized = (weirdness + 10000.0f) / 20000.0f;
        return Math.max(0.0f, Math.min(1.0f, normalized));
    }
}
