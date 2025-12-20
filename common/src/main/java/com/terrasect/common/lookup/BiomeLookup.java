package com.terrasect.common.lookup;

import com.terrasect.common.api.Region;
import com.terrasect.common.generation.definition.SelectionRules;
import com.terrasect.common.runtime.BiomeFilter;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A fast lookup structure for biome metadata and pre-baked filtered parameter lists.
 * 
 * <p>This class provides:
 * <ul>
 *   <li>Pre-computed biome IDs and tags for O(1) lookups</li>
 *   <li>Pre-baked filtered parameter lists for every SelectionRules in the region tree</li>
 * </ul>
 * 
 * <p>All filtering is done upfront during construction. The BiomeMixin can then retrieve
 * filtered parameter lists in O(1) with zero runtime filtering overhead.
 * 
 * <p>NOTE: This class intentionally avoids any Minecraft class references to prevent
 * Fabric Loom remapping issues. It uses Object keys with identity-based lookup,
 * so callers should pass Holder<Biome> instances directly as keys.
 * 
 * @param <K> The key type (typically Holder<Biome> but kept generic to avoid MC imports)
 * @param <P> The parameter list type (typically Climate.ParameterList<Holder<Biome>>)
 */
public final class BiomeLookup<K, P> {
    
    /**
     * Pre-computed metadata for a single biome.
     */
    public record Entry(String id, Set<String> tags) {}
    
    private final Map<K, Entry> metadata;
    
    // Pre-baked filtered parameter lists per SelectionRules (computed at construction)
    private final Map<SelectionRules, P> filteredParameterLists;
    
    // The original (unfiltered) parameter list
    private final P originalParameterList;
    
    private BiomeLookup(Map<K, Entry> metadata, P originalParameterList, Map<SelectionRules, P> filteredParameterLists) {
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
    public Entry get(K key) {
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
    public boolean isAllowed(K key, SelectionRules rules) {
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
    public BiomeFilter.FilterResult checkBiome(K key, SelectionRules rules) {
        Entry entry = metadata.get(key);
        if (entry == null) {
            return BiomeFilter.FilterResult.NO_RULES;
        }
        return BiomeFilter.checkBiome(rules, entry.id(), entry.tags());
    }
    
    /**
     * @return The number of biomes in this lookup
     */
    public int size() {
        return metadata.size();
    }
    
    /**
     * Get the original (unfiltered) parameter list.
     * 
     * @return The original parameter list
     */
    public P getParameterList() {
        return originalParameterList;
    }
    
    /**
     * Get the pre-baked filtered parameter list for the given rules.
     * O(1) lookup - all filtering was done at construction time.
     * 
     * @param rules The selection rules (null returns original list)
     * @return The filtered parameter list
     */
    public P getFilteredParameterList(SelectionRules rules) {
        if (rules == null || (!rules.hasAllowRules() && !rules.hasBlockRules())) {
            return originalParameterList;
        }
        P filtered = filteredParameterLists.get(rules);
        return filtered != null ? filtered : originalParameterList;
    }
    
    /**
     * @return The number of pre-baked filtered lists
     */
    public int getFilteredListCount() {
        return filteredParameterLists.size();
    }
    
    /**
     * Get cache statistics for debugging.
     */
    public String getCacheStats() {
        return String.format("BiomeLookup: %d biomes, %d pre-baked rule sets", 
            metadata.size(), filteredParameterLists.size());
    }

    /**
     * Create a new builder for constructing a BiomeLookup.
     * 
     * @param <K> The key type
     * @param <P> The parameter list type
     * @return A new builder instance
     */
    public static <K, P> Builder<K, P> builder() {
        return new Builder<>();
    }
    
    /**
     * Collect all unique SelectionRules from a region tree.
     * This walks the entire tree recursively to find every biome rule set.
     * 
     * @param root The root region
     * @return Set of all unique SelectionRules in the tree
     */
    public static Set<SelectionRules> collectAllRules(Region root) {
        Set<SelectionRules> rules = Collections.newSetFromMap(new IdentityHashMap<>());
        collectRulesRecursive(root, rules);
        return rules;
    }
    
    private static void collectRulesRecursive(Region region, Set<SelectionRules> rules) {
        if (region == null) return;
        
        SelectionRules biomeRules = region.definition().biomes();
        if (biomeRules != null && (biomeRules.hasAllowRules() || biomeRules.hasBlockRules())) {
            rules.add(biomeRules);
        }
        
        for (Region child : region.children()) {
            collectRulesRecursive(child, rules);
        }
    }
    
    /**
     * Builder for constructing BiomeLookup instances.
     * 
     * <p>Usage:
     * <pre>{@code
     * BiomeLookup<Holder<Biome>, Climate.ParameterList<Holder<Biome>>> lookup = 
     *     BiomeLookup.<Holder<Biome>, Climate.ParameterList<Holder<Biome>>>builder()
     *         .add(biome, getBiomeId(biome), getBiomeTags(biome))
     *         // ... add all biomes
     *         .withParameterList(originalList)
     *         .withRegionTree(dimensionRoot)
     *         .build(this::buildFilteredList);
     * }</pre>
     */
    public static final class Builder<K, P> {
        private final Map<K, Entry> metadata = new IdentityHashMap<>();
        private P originalParameterList;
        private Set<SelectionRules> rulesToPreBake;
        
        private Builder() {}
        
        /**
         * Add a biome with its pre-computed metadata.
         * 
         * @param key The biome holder (used as identity key)
         * @param id The biome's resource location string (e.g., "minecraft:plains")
         * @param tags The biome's tags with # prefix (e.g., "#minecraft:is_forest")
         * @return this builder for chaining
         */
        public Builder<K, P> add(K key, String id, Set<String> tags) {
            metadata.put(key, new Entry(id, tags));
            return this;
        }
        
        /**
         * Set the original (unfiltered) parameter list.
         * 
         * @param parameterList The original parameter list
         * @return this builder for chaining
         */
        public Builder<K, P> withParameterList(P parameterList) {
            this.originalParameterList = parameterList;
            return this;
        }
        
        /**
         * Extract all SelectionRules from the region tree for pre-baking.
         * 
         * @param root The root region of the dimension
         * @return this builder for chaining
         */
        public Builder<K, P> withRegionTree(Region root) {
            this.rulesToPreBake = collectAllRules(root);
            return this;
        }
        
        /**
         * Explicitly set the rules to pre-bake (alternative to withRegionTree).
         * 
         * @param rules The set of rules to pre-bake
         * @return this builder for chaining
         */
        public Builder<K, P> withRules(Set<SelectionRules> rules) {
            this.rulesToPreBake = rules;
            return this;
        }
        
        /**
         * Build the lookup with pre-baked filtered parameter lists.
         * 
         * @param filterFunction Function that takes (this lookup's metadata, original list, rules) 
         *                       and returns a filtered parameter list
         * @return A new BiomeLookup instance with all filtering pre-computed
         */
        public BiomeLookup<K, P> build(FilterFunction<K, P> filterFunction) {
            Map<K, Entry> finalMetadata = new IdentityHashMap<>(metadata);
            Map<SelectionRules, P> filtered = new IdentityHashMap<>();
            
            if (rulesToPreBake != null && originalParameterList != null) {
                for (SelectionRules rules : rulesToPreBake) {
                    if (rules != null && (rules.hasAllowRules() || rules.hasBlockRules())) {
                        P filteredList = filterFunction.filter(finalMetadata, originalParameterList, rules);
                        filtered.put(rules, filteredList);
                    }
                }
            }
            
            return new BiomeLookup<>(finalMetadata, originalParameterList, filtered);
        }
        
        /**
         * Build a simple lookup without parameter list support.
         * Use this when you only need biome metadata lookups.
         * 
         * @return A new BiomeLookup instance
         */
        public BiomeLookup<K, P> buildSimple() {
            return new BiomeLookup<>(new IdentityHashMap<>(metadata), null, Collections.emptyMap());
        }
    }
    
    /**
     * Functional interface for building filtered parameter lists.
     */
    @FunctionalInterface
    public interface FilterFunction<K, P> {
        /**
         * Filter a parameter list based on selection rules.
         * 
         * @param metadata The biome metadata map
         * @param original The original parameter list
         * @param rules The selection rules to apply
         * @return The filtered parameter list
         */
        P filter(Map<K, Entry> metadata, P original, SelectionRules rules);
    }
}
