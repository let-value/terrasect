package com.terrasect.common.lookup;

import com.terrasect.common.generation.definition.SelectionRules;
import com.terrasect.common.runtime.BiomeFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A fast lookup structure for biome metadata and filtering.
 * 
 * <p>This class provides:
 * <ul>
 *   <li>Pre-computed biome IDs and tags for O(1) lookups</li>
 *   <li>Cached filtered entry lists per SelectionRules</li>
 * </ul>
 * 
 * <p>The caching is critical for performance: instead of iterating 7000+ entries
 * on every biome lookup, we pre-filter once per unique SelectionRules.
 * 
 * <p>NOTE: This class intentionally avoids any Minecraft class references to prevent
 * Fabric Loom remapping issues. It uses Object keys with identity-based lookup,
 * so callers should pass Holder<Biome> instances directly as keys.
 * 
 * @param <K> The key type (typically Holder<Biome> but kept generic to avoid MC imports)
 */
public final class BiomeLookup<K> {
    
    /**
     * Pre-computed metadata for a single biome.
     */
    public record Entry(String id, Set<String> tags) {}
    
    /**
     * Result of filtering an entry list.
     */
    public record FilteredEntries<E>(
        List<E> entries,
        int originalSize,
        int filteredSize
    ) {
        public boolean isEmpty() {
            return filteredSize == 0;
        }
    }
    
    private final Map<K, Entry> metadata;
    private final Set<K> allBiomes;
    
    // Cache filtered biome sets by SelectionRules
    // Key: SelectionRules, Value: Set of allowed biome keys
    private final Map<SelectionRules, Set<K>> allowedBiomeCache = new ConcurrentHashMap<>();
    
    private BiomeLookup(Map<K, Entry> metadata) {
        this.metadata = metadata;
        this.allBiomes = Collections.unmodifiableSet(metadata.keySet());
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
     * Get the set of allowed biomes for the given rules.
     * Results are cached for performance.
     * 
     * @param rules The selection rules (null = all allowed)
     * @return Set of allowed biome keys (identity-based)
     */
    public Set<K> getAllowedBiomes(SelectionRules rules) {
        if (rules == null || (!rules.hasAllowRules() && !rules.hasBlockRules())) {
            return allBiomes;
        }
        return allowedBiomeCache.computeIfAbsent(rules, this::computeAllowedBiomes);
    }
    
    /**
     * Filter a list of entries, keeping only those with allowed biomes.
     * Uses cached allowed biome set for O(1) per-entry checks.
     * 
     * @param entries The entries to filter
     * @param biomeExtractor Function to get biome key from entry
     * @param rules The selection rules
     * @param <E> Entry type
     * @return Filtered entries with metadata
     */
    public <E> FilteredEntries<E> filterEntries(
            List<E> entries,
            java.util.function.Function<E, K> biomeExtractor,
            SelectionRules rules) {
        
        if (rules == null || (!rules.hasAllowRules() && !rules.hasBlockRules())) {
            return new FilteredEntries<>(entries, entries.size(), entries.size());
        }
        
        Set<K> allowed = getAllowedBiomes(rules);
        List<E> filtered = new ArrayList<>();
        
        for (E entry : entries) {
            K biome = biomeExtractor.apply(entry);
            if (allowed.contains(biome)) {
                filtered.add(entry);
            }
        }
        
        return new FilteredEntries<>(filtered, entries.size(), filtered.size());
    }
    
    /**
     * Compute which biomes are allowed for the given rules.
     * Called once per unique SelectionRules, then cached.
     */
    private Set<K> computeAllowedBiomes(SelectionRules rules) {
        Set<K> allowed = Collections.newSetFromMap(new IdentityHashMap<>());
        
        for (Map.Entry<K, Entry> entry : metadata.entrySet()) {
            K biome = entry.getKey();
            Entry meta = entry.getValue();
            
            if (BiomeFilter.checkBiome(rules, meta.id(), meta.tags()) != BiomeFilter.FilterResult.BLOCKED) {
                allowed.add(biome);
            }
        }
        
        return Collections.unmodifiableSet(allowed);
    }
    
    /**
     * Get cache statistics for debugging.
     */
    public String getCacheStats() {
        return String.format("BiomeLookup: %d biomes, %d cached rule sets", 
            metadata.size(), allowedBiomeCache.size());
    }

    /**
     * Create a new builder for constructing a BiomeMetadataLookup.
     * 
     * @param <K> The key type
     * @return A new builder instance
     */
    public static <K> Builder<K> builder() {
        return new Builder<>();
    }
    
    /**
     * Builder for constructing BiomeMetadataLookup instances.
     */
    public static final class Builder<K> {
        private final Map<K, Entry> metadata = new IdentityHashMap<>();
        
        private Builder() {}
        
        /**
         * Add a biome with its pre-computed metadata.
         * 
         * @param key The biome holder (used as identity key)
         * @param id The biome's resource location string (e.g., "minecraft:plains")
         * @param tags The biome's tags with # prefix (e.g., "#minecraft:is_forest")
         * @return this builder for chaining
         */
        public Builder<K> add(K key, String id, Set<String> tags) {
            metadata.put(key, new Entry(id, tags));
            return this;
        }
        
        /**
         * Build the immutable lookup structure.
         * 
         * @return A new BiomeMetadataLookup instance
         */
        public BiomeLookup<K> build() {
            return new BiomeLookup<>(new IdentityHashMap<>(metadata));
        }
    }
}
