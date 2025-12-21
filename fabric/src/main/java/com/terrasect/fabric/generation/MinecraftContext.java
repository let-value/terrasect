package com.terrasect.fabric.generation;

import static com.terrasect.common.compat.ResourceKeyCompat.getKeyId;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.terrasect.common.api.Context;
import com.terrasect.common.api.DimensionRoots;
import com.terrasect.common.api.Region;
import com.terrasect.common.generation.definition.SelectionRules;
import com.terrasect.common.lookup.BiomeLookup;
import com.terrasect.common.runtime.BiomeFilter;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric-specific generation context that provides O(1) biome filtering.
 * 
 * <p>
 * Created once per dimension when the world loads. Holds a {@link BiomeLookup}
 * with
 * pre-baked filtered parameter lists for every {@link SelectionRules} in the
 * region tree.
 */
public class MinecraftContext implements Context {

    // Global registry of contexts by dimension and sampler
    private static final Map<ResourceKey<Level>, MinecraftContext> BY_DIMENSION = new ConcurrentHashMap<>();
    private static final Map<Climate.Sampler, MinecraftContext> BY_SAMPLER = new ConcurrentHashMap<>();

    private final long seed;
    private final Climate.Sampler sampler;
    private final Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters;
    private final String dimensionId;
    private final BiomeLookup<Holder<Biome>, Climate.ParameterList<Holder<Biome>>> biomeLookup;
    private final Climate.ParameterList<Holder<Biome>> paramList;

    private MinecraftContext(long seed, String dimensionId, Climate.Sampler sampler,
            Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters,
            BiomeLookup<Holder<Biome>, Climate.ParameterList<Holder<Biome>>> biomeLookup) {
        this.seed = seed;
        this.dimensionId = dimensionId;
        this.sampler = sampler;
        this.parameters = parameters;
        this.paramList = parameters.map(list -> list, holder -> holder.value().parameters());
        this.biomeLookup = biomeLookup;
    }

    public static MinecraftContext create(
            ResourceKey<Level> dimension,
            long seed,
            Climate.Sampler sampler,
            Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters) {

        String dimensionId = getKeyId(dimension);
        var lookup = buildLookup(parameters, dimensionId);

        MinecraftContext context = new MinecraftContext(seed, dimensionId, sampler, parameters, lookup);

        BY_DIMENSION.put(dimension, context);
        if (sampler != null) {
            BY_SAMPLER.put(sampler, context);
        }

        return context;
    }

    /**
     * Build the BiomeLookup with pre-baked filtered parameter lists.
     */
    private static BiomeLookup<Holder<Biome>, Climate.ParameterList<Holder<Biome>>> buildLookup(
            Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters,
            String dimensionId) {

        if (parameters == null) {
            return BiomeLookup.<Holder<Biome>, Climate.ParameterList<Holder<Biome>>>builder().buildSimple();
        }

        Climate.ParameterList<Holder<Biome>> paramList = parameters.map(
                list -> list,
                holder -> holder.value().parameters());

        // Collect biome metadata
        BiomeLookup.Builder<Holder<Biome>, Climate.ParameterList<Holder<Biome>>> builder = BiomeLookup.builder();
        Set<Holder<Biome>> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        for (var entry : paramList.values()) {
            Holder<Biome> biome = entry.getSecond();
            if (seen.add(biome)) {
                String id = biome.unwrapKey().map(k -> getKeyId(k)).orElse("unknown");
                Set<String> tags = new HashSet<>();
                biome.tags().forEach(tag -> tags.add("#" + tag.location().toString()));
                builder.add(biome, id, tags);
            }
        }

        builder.withParameterList(paramList);

        // Pre-bake filtered lists for all rules in the region tree
        Region root = DimensionRoots.getRoot(dimensionId);
        if (root != null) {
            builder.withRegionTree(root);
        }

        return builder.build((metadata, original, rules) -> {
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> filtered = new ArrayList<>();

            for (var entry : original.values()) {
                Holder<Biome> biome = entry.getSecond();
                BiomeLookup.Entry meta = metadata.get(biome);

                boolean allowed = meta == null ||
                        BiomeFilter.checkBiome(rules, meta.id(), meta.tags()) != BiomeFilter.FilterResult.BLOCKED;

                if (allowed) {
                    filtered.add(entry);
                }
            }

            // Fallback: keep first biome if everything was filtered
            if (filtered.isEmpty() && !original.values().isEmpty()) {
                filtered.add(original.values().get(0));
            }

            return new Climate.ParameterList<>(filtered);
        });
    }

    // ==================== Lookup ====================

    public static MinecraftContext get(ResourceKey<Level> dimension) {
        return BY_DIMENSION.get(dimension);
    }

    public static MinecraftContext get(Climate.Sampler sampler) {
        MinecraftContext ctx = BY_SAMPLER.get(sampler);
        if (ctx != null)
            return ctx;

        // Fallback: try overworld, then any context
        ctx = BY_DIMENSION.get(Level.OVERWORLD);
        if (ctx != null)
            return ctx;

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

    public BiomeLookup<Holder<Biome>, Climate.ParameterList<Holder<Biome>>> getBiomeLookup() {
        return biomeLookup;
    }

    /**
     * Get a filtered parameter list for the given rules. O(1) lookup.
     */
    public Climate.ParameterList<Holder<Biome>> getFilteredParameterList(SelectionRules rules) {
        return biomeLookup.getFilteredParameterList(rules);
    }

    /**
     * Extract biome ID from a holder (for debug/logging).
     */
    public static String getBiomeId(Holder<Biome> biome) {
        return biome.unwrapKey().map(k -> getKeyId(k)).orElse("unknown");
    }

    // ==================== Context Interface ====================

    @Override
    public float getRiverInfluence(int x, int z) {
        if (sampler == null)
            return 0.0f;

        Climate.TargetPoint target = ((VanillaSampler) (Object) sampler)
                .terrasect$sampleVanilla(x >> 2, 16, z >> 2);

        Holder<Biome> biome = paramList.findValue(target);
        return biome.is(BiomeTags.IS_RIVER) ? 1.0f : 0.0f;
    }

    @Override
    public float getRidgeInfluence(int x, int z) {
        if (sampler == null)
            return 0.0f;

        Climate.TargetPoint target = ((VanillaSampler) (Object) sampler)
                .terrasect$sampleVanilla(x >> 2, 16, z >> 2);

        long weirdness = target.weirdness();
        float normalized = (weirdness + 10000.0f) / 20000.0f;
        return Math.max(0.0f, Math.min(1.0f, normalized));
    }
}
