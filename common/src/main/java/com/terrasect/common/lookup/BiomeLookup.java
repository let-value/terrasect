package com.terrasect.common.lookup;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.terrasect.common.compat.BiomeCompat;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.SelectionRules;
import com.terrasect.common.generation.World;
import com.terrasect.common.helpers.BiomeFilter;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Pre-computed biome metadata and pre-baked filtered parameter lists.
 *
 * <p>Built once per dimension; runtime lookups are O(1) with no allocations.
 */
public final class BiomeLookup {
    
    /**
     * Pre-computed metadata for a single biome.
     */
    public record Entry(String id, Set<String> tags) {}

    public static BiomeLookup metadataOnly(IdentityHashMap<Holder<Biome>, Entry> metadata) {
        return new BiomeLookup(metadata, null, new IdentityHashMap<>());
    }
    
    private final IdentityHashMap<Holder<Biome>, Entry> metadata;
    private final Climate.ParameterList<Holder<Biome>> originalParameterList;
    private final IdentityHashMap<SelectionRules, Climate.ParameterList<Holder<Biome>>> filteredParameterLists;
    
    private BiomeLookup(
            IdentityHashMap<Holder<Biome>, Entry> metadata,
            Climate.ParameterList<Holder<Biome>> originalParameterList,
            IdentityHashMap<SelectionRules, Climate.ParameterList<Holder<Biome>>> filteredParameterLists) {
        this.metadata = metadata;
        this.originalParameterList = originalParameterList;
        this.filteredParameterLists = filteredParameterLists;
    }
    
    /**
     * Get the pre-computed metadata for a biome.
     * 
     * @param key The biome holder (identity-based lookup)
     * @return The metadata entry, or null if not found
     */
    public Entry get(Holder<Biome> key) {
        return metadata.get(key);
    }
    
    /**
     * Check if a biome is allowed by the given selection rules.
     * Uses pre-computed metadata for O(1) lookup.
     * 
     * @param key The biome holder
     * @param rules The selection rules to check against
     * @return true if the biome is allowed (not blocked)
     */
    public boolean isAllowed(Holder<Biome> key, SelectionRules rules) {
        Entry entry = metadata.get(key);
        if (entry == null) {
            return true; // Unknown biomes are allowed by default
        }
        return BiomeFilter.checkBiome(rules, entry.id(), entry.tags()) != BiomeFilter.FilterResult.BLOCKED;
    }
    
    /**
     * Check a biome against selection rules and return the filter result.
     * 
     * @param key The biome holder
     * @param rules The selection rules to check against
     * @return The filter result
     */
    public BiomeFilter.FilterResult checkBiome(Holder<Biome> key, SelectionRules rules) {
        Entry entry = metadata.get(key);
        if (entry == null) {
            return BiomeFilter.FilterResult.NO_RULES;
        }
        return BiomeFilter.checkBiome(rules, entry.id(), entry.tags());
    }
    
    /**
     * Get the original (unfiltered) parameter list.
     * 
     * @return The original parameter list
     */
    public Climate.ParameterList<Holder<Biome>> getParameterList() {
        return originalParameterList;
    }
    
    /**
     * Get the pre-baked filtered parameter list for the given rules.
     * O(1) lookup - all filtering was done at construction time.
     * 
     * @param rules The selection rules (null returns original list)
     * @return The filtered parameter list
     */
    public Climate.ParameterList<Holder<Biome>> getFilteredParameterList(SelectionRules rules) {
        if (rules == null || (!rules.hasAllowRules() && !rules.hasBlockRules())) {
            return originalParameterList;
        }
        Climate.ParameterList<Holder<Biome>> filtered = filteredParameterLists.get(rules);
        return filtered != null ? filtered : originalParameterList;
    }
    
    /**
     * Convenience builder for Minecraft's multi-noise biome parameters.
     * Builds biome metadata and pre-bakes filtered parameter lists for all biome rules in the
     * dimension's region tree.
     */
    public static BiomeLookup build(
            Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters,
            String dimensionId) {

        if (parameters == null) {
            return new BiomeLookup(new IdentityHashMap<>(), null, new IdentityHashMap<>());
        }

        Climate.ParameterList<Holder<Biome>> parameterList = parameters.map(
            list -> list,
            holder -> holder.value().parameters()
        );

        IdentityHashMap<Holder<Biome>, Entry> metadata = new IdentityHashMap<>();
        for (Pair<Climate.ParameterPoint, Holder<Biome>> entry : parameterList.values()) {
            Holder<Biome> biome = entry.getSecond();
            if (metadata.containsKey(biome)) {
                continue;
            }

            String id = BiomeCompat.getBiomeId(biome);
            metadata.put(biome, new Entry(id, buildTags(biome)));
        }

        IdentityHashMap<SelectionRules, Climate.ParameterList<Holder<Biome>>> filteredParameterLists =
                buildFilteredLists(dimensionId, metadata, parameterList);

        return new BiomeLookup(metadata, parameterList, filteredParameterLists);
    }
    
    /**
     * Collect all unique {@link SelectionRules} from a region tree.
     */
    private static Set<SelectionRules> collectAllRules(Region root) {
        Set<SelectionRules> rules = Collections.newSetFromMap(new IdentityHashMap<>());
        collectRulesRecursive(root, rules);
        return rules;
    }
    
    private static void collectRulesRecursive(Region region, Set<SelectionRules> rules) {
        SelectionRules biomeRules = region.definition().biomes();
        if (biomeRules != null && (biomeRules.hasAllowRules() || biomeRules.hasBlockRules())) {
            rules.add(biomeRules);
        }
        
        for (Region child : region.children()) {
            collectRulesRecursive(child, rules);
        }
    }
    
    private static Set<String> buildTags(Holder<Biome> biome) {
        Set<String> tags = new HashSet<>();
        BiomeCompat.getTags(biome).forEach(tag -> tags.add("#" + tag.location().toString()));
        return tags;
    }

    private static IdentityHashMap<SelectionRules, Climate.ParameterList<Holder<Biome>>> buildFilteredLists(
            String dimensionId,
            IdentityHashMap<Holder<Biome>, Entry> metadata,
            Climate.ParameterList<Holder<Biome>> original) {

        Region root = World.getRoot(dimensionId);
        if (root == null) {
            return new IdentityHashMap<>();
        }

        Set<SelectionRules> rulesToPreBake = collectAllRules(root);
        if (rulesToPreBake.isEmpty()) {
            return new IdentityHashMap<>();
        }

        List<Pair<Climate.ParameterPoint, Holder<Biome>>> originalValues = original.values();
        IdentityHashMap<SelectionRules, Climate.ParameterList<Holder<Biome>>> filteredLists = new IdentityHashMap<>();

        for (SelectionRules rules : rulesToPreBake) {
            if (rules == null || (!rules.hasAllowRules() && !rules.hasBlockRules())) {
                continue;
            }

            List<Pair<Climate.ParameterPoint, Holder<Biome>>> filtered = new ArrayList<>(originalValues.size());
            for (Pair<Climate.ParameterPoint, Holder<Biome>> entry : originalValues) {
                Holder<Biome> biome = entry.getSecond();
                Entry meta = metadata.get(biome);

                boolean allowed = meta == null
                        || BiomeFilter.checkBiome(rules, meta.id(), meta.tags()) != BiomeFilter.FilterResult.BLOCKED;

                if (allowed) {
                    filtered.add(entry);
                }
            }

            if (filtered.isEmpty() && !originalValues.isEmpty()) {
                filtered.add(originalValues.get(0));
            }

            filteredLists.put(rules, new Climate.ParameterList<>(filtered));
        }

        return filteredLists;
    }
}
