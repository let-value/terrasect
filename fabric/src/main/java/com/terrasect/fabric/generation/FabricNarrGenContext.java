package com.terrasect.fabric.generation;

import com.mojang.datafixers.util.Either;
import com.terrasect.common.api.Context;
import com.terrasect.common.lookup.BiomeMetadataLookup;
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
 * Fabric-specific implementation of the generation context.
 * 
 * <p>This stores the dimension ID and climate sampler for a dimension,
 * enabling dimension-aware region lookups during world generation.
 * 
 * <p>Also pre-builds {@link BiomeMetadataLookup} for O(1) biome filtering,
 * so mixins don't need any initialization logic.
 */
public class FabricContext implements Context {
    
    private static final Map<ResourceKey<Level>, FabricContext> CONTEXTS = new ConcurrentHashMap<>();
    private static final Map<Climate.Sampler, FabricContext> BY_SAMPLER = new ConcurrentHashMap<>();

    private final long seed;
    private final Climate.Sampler sampler;
    private final Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters;
    private final String dimensionId;
    private final BiomeMetadataLookup<Holder<Biome>> biomeLookup;

    public FabricContext(long seed, Climate.Sampler sampler, Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters) {
        this(seed, sampler, parameters, "minecraft:overworld");
    }
    
    public FabricContext(long seed, Climate.Sampler sampler, Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters, String dimensionId) {
        this.seed = seed;
        this.sampler = sampler;
        this.parameters = parameters;
        this.dimensionId = dimensionId;
        this.biomeLookup = buildBiomeLookup(parameters);
    }
    
    /**
     * Build the biome metadata lookup for O(1) filtering.
     * Called once during context construction.
     */
    private static BiomeMetadataLookup<Holder<Biome>> buildBiomeLookup(
            Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters) {
        if (parameters == null) {
            return BiomeMetadataLookup.<Holder<Biome>>builder().build();
        }
        
        Climate.ParameterList<Holder<Biome>> paramList = parameters.map(
            list -> list,
            holder -> holder.value().parameters()
        );
        
        BiomeMetadataLookup.Builder<Holder<Biome>> builder = BiomeMetadataLookup.builder();
        Set<Holder<Biome>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        
        for (var entry : paramList.values()) {
            Holder<Biome> biome = entry.getSecond();
            if (seen.add(biome)) {
                String biomeId = getBiomeId(biome);
                Set<String> tags = getBiomeTags(biome);
                builder.add(biome, biomeId, tags);
            }
        }
        
        return builder.build();
    }
    
    private static String getBiomeId(Holder<Biome> biome) {
        return biome.unwrapKey()
            .map(key -> key.identifier().toString())
            .orElse("unknown");
    }
    
    private static Set<String> getBiomeTags(Holder<Biome> biome) {
        Set<String> tags = new HashSet<>();
        biome.tags().forEach(tag -> tags.add("#" + tag.location().toString()));
        return tags;
    }
    
    /**
     * Create a context from a Minecraft ResourceKey dimension.
     */
    public static FabricContext create(ResourceKey<Level> dimension, long seed, Climate.Sampler sampler, 
            Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters) {
        // Convert ResourceKey to string dimension ID
        String dimId = toDimensionId(dimension);
        return new FabricContext(seed, sampler, parameters, dimId);
    }
    
    /**
     * Convert a ResourceKey to a dimension ID string.
     * Uses known dimension mappings to avoid reflection/mapping issues.
     */
    private static String toDimensionId(ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) {
            return "minecraft:overworld";
        } else if (dimension == Level.NETHER) {
            return "minecraft:the_nether";
        } else if (dimension == Level.END) {
            return "minecraft:the_end";
        }
        // For modded dimensions, use toString() which includes the full path
        // Format is typically: "ResourceKey[minecraft:dimension / modid:dimname]"
        String str = dimension.toString();
        int slashIdx = str.indexOf(" / ");
        if (slashIdx > 0) {
            int endIdx = str.indexOf(']', slashIdx);
            if (endIdx > slashIdx) {
                return str.substring(slashIdx + 3, endIdx);
            }
        }
        // Fallback to overworld if we can't parse
        return "minecraft:overworld";
    }

    public static void register(ResourceKey<Level> dimension, FabricContext context) {
        CONTEXTS.put(dimension, context);
        if (context.sampler != null) {
            BY_SAMPLER.put(context.sampler, context);
        }
    }

    public static FabricContext get(ResourceKey<Level> dimension) {
        return CONTEXTS.get(dimension);
    }

    public static FabricContext get(Climate.Sampler sampler) {
        FabricContext ctx = BY_SAMPLER.get(sampler);
        if (ctx != null) {
            return ctx;
        }
        // Fallback to Overworld context (most common case)
        ctx = CONTEXTS.get(Level.OVERWORLD);
        if (ctx != null) {
            return ctx;
        }
        // Last resort: return any registered context
        return CONTEXTS.values().stream().findFirst().orElse(null);
    }

    public static void clear() {
        CONTEXTS.clear();
        BY_SAMPLER.clear();
    }

    @Override
    public long getSeed() {
        return seed;
    }
    
    /**
     * Get the dimension ID for this context.
     * 
     * @return The dimension ID (e.g., "minecraft:overworld", "minecraft:the_end")
     */
    public String getDimensionId() {
        return dimensionId;
    }
    
    /**
     * Get the pre-built biome metadata lookup for this dimension.
     * Used by BiomeMixin for O(1) biome filtering.
     * 
     * @return The biome lookup for this dimension's biome source
     */
    public BiomeMetadataLookup<Holder<Biome>> getBiomeLookup() {
        return biomeLookup;
    }

    @Override
    public float getRiverInfluence(int x, int z) {
        if (sampler == null || parameters == null) return 0.0f;
        
        // Convert block coords to quart coords (approximate Y=sea level)
        int qx = x >> 2;
        int qy = 16; 
        int qz = z >> 2;
        
        Climate.TargetPoint target = sampler.sample(qx, qy, qz);
        
        // Get the parameter list (either direct or from holder)
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
        Climate.TargetPoint target = sampler.sample(qx, qy, qz);
        
        // target.weirdness() returns a quantized long in range approximately [-10000, 10000]
        // Normalize to [0, 1] range for ridge influence
        long weirdness = target.weirdness();
        // Clamp to expected range and normalize
        float normalized = (weirdness + 10000.0f) / 20000.0f;
        return Math.max(0.0f, Math.min(1.0f, normalized));
    }
}
