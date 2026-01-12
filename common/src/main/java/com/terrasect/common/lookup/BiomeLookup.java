package com.terrasect.common.lookup;

import com.mojang.datafixers.util.Pair;
import com.terrasect.common.compat.BiomeCompat;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.SelectionRules;
import com.terrasect.common.generation.World;
import com.terrasect.common.helpers.BiomeFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

public final class BiomeLookup {

    public record Entry(String id, Set<String> tags) {
    }

    public static BiomeLookup metadataOnly(IdentityHashMap<Holder<Biome>, Entry> metadata) {
        return new BiomeLookup(metadata, new IdentityHashMap<>());
    }

    private final IdentityHashMap<Holder<Biome>, Entry> metadata;
    private final IdentityHashMap<SelectionRules, Climate.ParameterList<Holder<Biome>>> filteredParameterLists;

    private BiomeLookup(
            IdentityHashMap<Holder<Biome>, Entry> metadata,
            IdentityHashMap<SelectionRules, Climate.ParameterList<Holder<Biome>>> filteredParameterLists) {
        this.metadata = metadata;
        this.filteredParameterLists = filteredParameterLists;
    }

    public Entry get(Holder<Biome> key) {
        return metadata.get(key);
    }

    public boolean isAllowed(Holder<Biome> key, SelectionRules rules) {
        var entry = metadata.get(key);
        if (entry == null) {
            return true;
        }
        return BiomeFilter.checkBiome(rules, entry.id(), entry.tags()) != BiomeFilter.FilterResult.BLOCKED;
    }

    public BiomeFilter.FilterResult checkBiome(Holder<Biome> key, SelectionRules rules) {
        var entry = metadata.get(key);
        if (entry == null) {
            return BiomeFilter.FilterResult.NO_RULES;
        }
        return BiomeFilter.checkBiome(rules, entry.id(), entry.tags());
    }

    public Climate.ParameterList<Holder<Biome>> getFilteredParameterList(SelectionRules rules) {
        if (rules == null || (!rules.hasAllowRules() && !rules.hasBlockRules())) {
            return null;
        }
        return filteredParameterLists.get(rules);
    }

    public static BiomeLookup build(
            Climate.ParameterList<Holder<Biome>> parameterList,
            String dimensionId) {

        if (parameterList == null) {
            return new BiomeLookup(new IdentityHashMap<>(), new IdentityHashMap<>());
        }

        var metadata = new IdentityHashMap<Holder<Biome>, Entry>();
        for (Pair<Climate.ParameterPoint, Holder<Biome>> entry : parameterList.values()) {
            Holder<Biome> biome = entry.getSecond();
            if (metadata.containsKey(biome)) {
                continue;
            }

            String id = BiomeCompat.getBiomeId(biome);
            metadata.put(biome, new Entry(id, buildTags(biome)));
        }

        var filteredParameterLists =
                buildFilteredLists(dimensionId, metadata, parameterList);

        return new BiomeLookup(metadata, filteredParameterLists);
    }

    private static Set<SelectionRules> collectAllRules(Region root) {
        Set<SelectionRules> rules = Collections.newSetFromMap(new IdentityHashMap<>());
        collectRulesRecursive(root, rules);
        return rules;
    }

    private static void collectRulesRecursive(Region region, Set<SelectionRules> rules) {
        var biomeRules = region.definition().biomes();
        if (biomeRules != null && (biomeRules.hasAllowRules() || biomeRules.hasBlockRules())) {
            rules.add(biomeRules);
        }

        for (Region child : region.children()) {
            collectRulesRecursive(child, rules);
        }
    }

    private static Set<String> buildTags(Holder<Biome> biome) {
        var tags = new HashSet<String>();
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

        var rulesToPreBake = collectAllRules(root);
        if (rulesToPreBake.isEmpty()) {
            return new IdentityHashMap<>();
        }

        List<Pair<Climate.ParameterPoint, Holder<Biome>>> originalValues = original.values();
        var filteredLists = new IdentityHashMap<SelectionRules, Climate.ParameterList<Holder<Biome>>>();

        for (SelectionRules rules : rulesToPreBake) {
            if (rules == null || (!rules.hasAllowRules() && !rules.hasBlockRules())) {
                continue;
            }

            var filtered = new ArrayList<Pair<Climate.ParameterPoint, Holder<Biome>>>(originalValues.size());
            for (Pair<Climate.ParameterPoint, Holder<Biome>> entry : originalValues) {
                Holder<Biome> biome = entry.getSecond();
                var meta = metadata.get(biome);

                var allowed = meta == null
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
