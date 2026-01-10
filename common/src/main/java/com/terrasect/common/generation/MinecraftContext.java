package com.terrasect.common.generation;

import static com.terrasect.common.compat.ResourceKeyCompat.getKeyId;

import com.mojang.datafixers.util.Either;
import com.terrasect.common.Context;
import com.terrasect.common.definition.SelectionRules;
import com.terrasect.common.lookup.BiomeLookup;
import com.terrasect.common.mixin.VanillaSamplerAccessor;
import com.terrasect.common.util.Packer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

/**
 * Loader-agnostic generation context that provides O(1) biome filtering.
 *
 * <p>Created once per dimension when the world loads. Holds a {@link BiomeLookup} with
 * pre-baked filtered parameter lists for every {@link SelectionRules} in the region tree.
 */
public class MinecraftContext implements Context {

    // Global registry of contexts by dimension and sampler
    private static final Map<ResourceKey<Level>, MinecraftContext> BY_DIMENSION = new ConcurrentHashMap<>();
    private static final Map<Climate.Sampler, MinecraftContext> BY_SAMPLER = new ConcurrentHashMap<>();

    private final long seed;
    private final String dimensionId;
    private final Climate.Sampler sampler;
    public final BiomeLookup biomeLookup;

    private MinecraftContext(long seed, String dimensionId, Climate.Sampler sampler, BiomeLookup biomeLookup) {
        this.seed = seed;
        this.dimensionId = dimensionId;
        this.sampler = sampler;
        this.biomeLookup = biomeLookup;
    }

    /**
     * Create and register a context for a dimension.
     */
    public static MinecraftContext create(
            ResourceKey<Level> dimension,
            long seed,
            Climate.Sampler sampler,
            Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters) {

        String dimensionId = getKeyId(dimension);
        BiomeLookup lookup = BiomeLookup.build(parameters, dimensionId);

        MinecraftContext context = new MinecraftContext(seed, dimensionId, sampler, lookup);

        BY_DIMENSION.put(dimension, context);
        if (sampler != null) {
            BY_SAMPLER.put(sampler, context);
        }

        World.initialize(context);

        return context;
    }

    // ==================== Lookup ====================

    public static MinecraftContext get(ResourceKey<Level> dimension) {
        return BY_DIMENSION.get(dimension);
    }

    public static MinecraftContext get(Climate.Sampler sampler) {
        MinecraftContext ctx = BY_SAMPLER.get(sampler);
        if (ctx != null) return ctx;

        // Fallback: try overworld, then any context
        ctx = BY_DIMENSION.get(Level.OVERWORLD);
        if (ctx != null) return ctx;

        return BY_DIMENSION.values().stream().findFirst().orElse(null);
    }

    public static void clear() {
        BY_DIMENSION.clear();
        BY_SAMPLER.clear();
    }

    // ==================== Getters ====================

    @Override
    public long getSeed() {
        return seed;
    }

    @Override
    public String getDimensionId() {
        return dimensionId;
    }

    @Override
    public long getInfluence(int x, int z) {
        Climate.ParameterList<Holder<Biome>> paramList = biomeLookup.getParameterList();
        if (sampler == null || paramList == null) return 0L;

        // Sample climate once for both river and ridge influence
        Climate.TargetPoint target =
                ((VanillaSamplerAccessor) (Object) sampler).terrasect$sampleVanilla(x >> 2, 16, z >> 2);

        // River influence: binary check against biome tag
        Holder<Biome> biome = paramList.findValue(target);
        float river = biome.is(BiomeTags.IS_RIVER) ? 1.0f : 0.0f;

        // Ridge influence: normalized weirdness
        long weirdness = target.weirdness();
        float normalized = (weirdness + 10000.0f) / 20000.0f;
        float ridge = Math.max(0.0f, Math.min(1.0f, normalized));

        return Packer.packPair(river, ridge);
    }
}
