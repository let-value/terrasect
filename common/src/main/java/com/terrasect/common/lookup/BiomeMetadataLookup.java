package com.terrasect.common.lookup;

import com.terrasect.common.generation.definition.SelectionRules;
import com.terrasect.common.runtime.BiomeFilter;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A fast lookup structure for biome metadata used during filtering.
 * 
 * This class pre-computes biome IDs and tags to enable O(1) lookups
 * during biome filtering, avoiding repeated string allocation in the hot path.
 * 
 * NOTE: This class intentionally avoids any Minecraft class references to prevent
 * Fabric Loom remapping issues. It uses Object keys with identity-based lookup,
 * so callers should pass Holder<Biome> instances directly as keys.
 * 
 * @param <K> The key type (typically Holder<Biome> but kept generic to avoid MC imports)
 */
public final class BiomeMetadataLookup<K> {
    
    /**
     * Pre-computed metadata for a single biome.
     */
    public record Entry(String id, Set<String> tags) {}
    
    private final Map<K, Entry> metadata;
    
    private BiomeMetadataLookup(Map<K, Entry> metadata) {
        this.metadata = metadata;
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
        public BiomeMetadataLookup<K> build() {
            return new BiomeMetadataLookup<>(new IdentityHashMap<>(metadata));
        }
    }
}
