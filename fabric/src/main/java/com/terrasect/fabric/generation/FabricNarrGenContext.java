package com.terrasect.fabric.generation;

import com.mojang.datafixers.util.Either;
import com.terrasect.common.api.Strategy;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric-specific implementation of the generation context.
 * 
 * <p>This stores the dimension ID and climate sampler for a dimension,
 * enabling dimension-aware region lookups during world generation.
 */
public class FabricNarrGenContext implements Strategy {
    
    private static final Map<ResourceKey<Level>, FabricNarrGenContext> CONTEXTS = new ConcurrentHashMap<>();
    private static final Map<Climate.Sampler, FabricNarrGenContext> BY_SAMPLER = new ConcurrentHashMap<>();

    private final long seed;
    private final Climate.Sampler sampler;
    private final Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters;
    private final String dimensionId;

    public FabricNarrGenContext(long seed, Climate.Sampler sampler, Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters) {
        this(seed, sampler, parameters, "minecraft:overworld");
    }
    
    public FabricNarrGenContext(long seed, Climate.Sampler sampler, Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters, String dimensionId) {
        this.seed = seed;
        this.sampler = sampler;
        this.parameters = parameters;
        this.dimensionId = dimensionId;
    }
    
    /**
     * Create a context from a Minecraft ResourceKey dimension.
     */
    public static FabricNarrGenContext create(ResourceKey<Level> dimension, long seed, Climate.Sampler sampler, 
            Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters) {
        // Convert ResourceKey to string dimension ID
        String dimId = toDimensionId(dimension);
        return new FabricNarrGenContext(seed, sampler, parameters, dimId);
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
        FabricNarrGenContext ctx = BY_SAMPLER.get(sampler);
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
