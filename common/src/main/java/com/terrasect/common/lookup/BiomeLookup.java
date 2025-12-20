package com.terrasect.common.lookup;

import com.terrasect.common.generation.definition.SelectionRules;
import com.terrasect.common.runtime.BiomeFilter;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.*;
import java.util.function.Function;

/**
 * Pre-computed biome metadata lookup for fast filtering.
 * 
 * This class builds a lookup table from the Minecraft biome registry,
 * storing each biome's ID, namespace, and tags. This allows BiomeFilter
 * to check biomes in O(1) time without any string allocation.
 * 
 * Usage:
 * 1. Build once from registry: BiomeLookup lookup = BiomeLookup.fromRegistry(biomeRegistry);
 * 2. Use in hot path: lookup.isAllowed(biomeHolder, rules);
 * 
 * Thread-safety: Immutable after construction. Safe for concurrent read access.
 */
public final class BiomeLookup {
    
    /**
     * Pre-computed metadata for a single biome.
     * All fields are immutable and safe for concurrent access.
     */
    public record BiomeMetadata(
        Holder<Biome> holder,  // Original holder for biome access
        String id,             // Full ID like "minecraft:plains"
        String namespace,      // Just namespace like "minecraft"
        Set<String> tags       // Immutable set of tags like "#minecraft:is_forest"
    ) {
        public BiomeMetadata {
            // Ensure tags is immutable
            tags = Set.copyOf(tags);
        }
    }
    
    // IdentityHashMap for O(1) lookup by biome holder reference
    private final IdentityHashMap<Holder<Biome>, BiomeMetadata> metadataByHolder;
    
    // Map from biome ID to metadata for ID-based lookups
    private final Map<String, BiomeMetadata> metadataById;
    
    // All biomes in the registry, for iteration when finding fallbacks
    private final List<BiomeMetadata> allBiomes;
    
    private BiomeLookup(
            IdentityHashMap<Holder<Biome>, BiomeMetadata> metadataByHolder,
            Map<String, BiomeMetadata> metadataById,
            List<BiomeMetadata> allBiomes) {
        this.metadataByHolder = metadataByHolder;
        this.metadataById = metadataById;
        this.allBiomes = allBiomes;
    }
    
    /**
     * Build a BiomeLookup from a Minecraft biome registry.
     * This should be called once during world load and cached.
     * 
     * @param registry The biome registry (from RegistryAccess)
     * @return A new BiomeLookup with all biomes indexed
     */
    public static BiomeLookup fromRegistry(Registry<Biome> registry) {
        IdentityHashMap<Holder<Biome>, BiomeMetadata> byHolder = new IdentityHashMap<>();
        Map<String, BiomeMetadata> byId = new HashMap<>();
        List<BiomeMetadata> all = new ArrayList<>();
        
        // Use listElements() to iterate over all registered biomes
        registry.listElements().forEach(holder -> {
            ResourceKey<Biome> key = holder.key();
            Identifier location = key.identifier();
            
            String id = location.toString();
            String namespace = location.getNamespace();
            
            // Collect all tags for this biome
            Set<String> tags = new HashSet<>();
            holder.tags().forEach(tag -> {
                // Store both with and without # prefix for fast lookup
                String tagId = tag.location().toString();
                tags.add("#" + tagId);
                tags.add(tagId);
            });
            
            BiomeMetadata metadata = new BiomeMetadata(holder, id, namespace, tags);
            byHolder.put(holder, metadata);
            byId.put(id, metadata);
            all.add(metadata);
        });
        
        return new BiomeLookup(byHolder, byId, List.copyOf(all));
    }
    
    /**
     * Create a new builder for BiomeLookup.
     * Use this to add biomes one by one without depending on Climate.ParameterList.
     * 
     * @return A new builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for BiomeLookup.
     * Allows adding biomes one by one with their pre-computed metadata.
     */
    public static final class Builder {
        private final IdentityHashMap<Holder<Biome>, BiomeMetadata> byHolder = new IdentityHashMap<>();
        private final Map<String, BiomeMetadata> byId = new HashMap<>();
        private final List<BiomeMetadata> all = new ArrayList<>();
        
        private Builder() {}
        
        /**
         * Add a biome with its pre-computed metadata.
         * 
         * @param holder The biome holder
         * @param id Full biome ID like "minecraft:plains"
         * @param tags Set of tags for this biome
         * @return this builder for chaining
         */
        public Builder add(Holder<Biome> holder, String id, Set<String> tags) {
            // Skip duplicates
            if (byHolder.containsKey(holder)) {
                return this;
            }
            
            int colonIndex = id.indexOf(':');
            String namespace = colonIndex > 0 ? id.substring(0, colonIndex) : "minecraft";
            
            BiomeMetadata metadata = new BiomeMetadata(holder, id, namespace, tags);
            byHolder.put(holder, metadata);
            byId.put(id, metadata);
            all.add(metadata);
            return this;
        }
        
        /**
         * Build the immutable BiomeLookup.
         * 
         * @return A new BiomeLookup with all added biomes
         */
        public BiomeLookup build() {
            return new BiomeLookup(byHolder, byId, List.copyOf(all));
        }
    }
    
    /**
     * Get pre-computed metadata for a biome holder.
     * O(1) lookup using identity comparison.
     * 
     * @param biome The biome holder
     * @return Metadata or null if biome not in registry
     */
    public BiomeMetadata getMetadata(Holder<Biome> biome) {
        return metadataByHolder.get(biome);
    }
    
    /**
     * Get pre-computed metadata for a biome by ID.
     * 
     * @param biomeId The full biome ID (e.g., "minecraft:plains")
     * @return Metadata or null if biome not found
     */
    public BiomeMetadata getMetadataById(String biomeId) {
        return metadataById.get(biomeId);
    }
    
    /**
     * Get all biomes in the lookup.
     * Useful for finding fallback biomes.
     * 
     * @return Unmodifiable list of all biome metadata
     */
    public List<BiomeMetadata> getAllBiomes() {
        return allBiomes;
    }
    
    /**
     * Check if a biome is allowed by the selection rules.
     * This is the fast path - O(1) lookups with no allocation.
     * 
     * @param biome The biome holder to check
     * @param rules The selection rules
     * @return FilterResult indicating if biome is allowed
     */
    public BiomeFilter.FilterResult checkBiome(Holder<Biome> biome, SelectionRules rules) {
        BiomeMetadata metadata = metadataByHolder.get(biome);
        if (metadata == null) {
            // Unknown biome - allow by default
            return BiomeFilter.FilterResult.NO_RULES;
        }
        return BiomeFilter.checkBiome(rules, metadata.id(), metadata.tags());
    }
    
    /**
     * Check if a biome is allowed (convenience boolean version).
     * 
     * @param biome The biome holder to check
     * @param rules The selection rules
     * @return true if allowed, false if blocked
     */
    public boolean isAllowed(Holder<Biome> biome, SelectionRules rules) {
        return checkBiome(biome, rules) != BiomeFilter.FilterResult.BLOCKED;
    }
    
    /**
     * Check if a biome metadata is allowed by the selection rules.
     * Use this when you already have the metadata (e.g., when iterating allBiomes).
     * 
     * @param metadata Pre-computed biome metadata
     * @param rules The selection rules
     * @return true if allowed, false if blocked
     */
    public boolean isAllowed(BiomeMetadata metadata, SelectionRules rules) {
        return BiomeFilter.isAllowed(rules, metadata.id(), metadata.tags());
    }
    
    /**
     * Get the number of biomes in the lookup.
     */
    public int size() {
        return allBiomes.size();
    }
}
