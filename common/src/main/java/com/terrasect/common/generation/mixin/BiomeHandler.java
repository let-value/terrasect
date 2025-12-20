package com.terrasect.common.generation.mixin;

import com.terrasect.common.generation.BiomeFilter;
import com.terrasect.common.generation.Region;
import com.terrasect.common.generation.Strategy;
import com.terrasect.common.generation.World;
import com.terrasect.common.generation.debug.MixinSampler;
import com.terrasect.common.generation.definition.SelectionRules;

import java.util.Set;

/**
 * Shared biome filtering logic for platform mixins.
 * 
 * This class contains all the common biome handling code that is shared
 * between Fabric and NeoForge BiomeMixin implementations.
 * 
 * Note: The actual ParameterList filtering is done in the platform mixins
 * since it requires Minecraft classes. This class handles the region lookup
 * and rule retrieval logic.
 */
public final class BiomeHandler {
    
    private BiomeHandler() {
        // Static utility class
    }
    
    /**
     * Result of biome filtering check.
     */
    public record FilterContext(
        SelectionRules rules,
        boolean shouldFilter,
        String regionName
    ) {
        public static FilterContext noFilter() {
            return new FilterContext(null, false, null);
        }
    }
    
    /**
     * Get the biome filtering context for a location.
     * 
     * @param context The generation strategy context (null if not available)
     * @param x Biome coordinate X (not block coordinate)
     * @param z Biome coordinate Z
     * @return FilterContext indicating whether filtering should occur and with what rules
     */
    public static FilterContext getFilterContext(Strategy context, int x, int z) {
        if (context == null) {
            return FilterContext.noFilter();
        }

        int blockX = x << 2;
        int blockZ = z << 2;
        
        // Get the region at this location
        Region region = World.getRegion(blockX, blockZ, context);
        if (region == null) {
            return FilterContext.noFilter();
        }
        
        // Get biome selection rules for this region
        SelectionRules biomeRules = region.definition().biomes();
        if (!BiomeFilter.hasRules(biomeRules)) {
            return FilterContext.noFilter();
        }
        
        return new FilterContext(biomeRules, true, region.name());
    }
    
    /**
     * Record a biome filter result for sampling.
     * Called by platform mixins after biome selection.
     */
    public static void recordBiomeSelection(int quartX, int quartZ, String selectedBiome, 
                                             String regionName, boolean wasFiltered) {
        MixinSampler.recordBiomeFilter(quartX, quartZ, selectedBiome, regionName, wasFiltered);
    }

    /**
     * Check if a biome is allowed based on the rules.
     * 
     * @param rules The selection rules to apply
     * @param biomeId The biome's resource location as a string
     * @param biomeTags Set of tag strings for the biome (prefixed with #)
     * @return true if the biome is allowed, false if blocked
     */
    public static boolean isBiomeAllowed(SelectionRules rules, String biomeId, Set<String> biomeTags) {
        return BiomeFilter.isAllowed(rules, biomeId, biomeTags);
    }
    
    /**
     * Compute a cache key for SelectionRules to avoid rebuilding filtered lists.
     * 
     * @param rules The selection rules
     * @return A hash code suitable for use as a cache key
     */
    public static int computeRulesCacheKey(SelectionRules rules) {
        if (rules == null) return 0;
        int hash = 17;
        hash = 31 * hash + rules.allowedMods().hashCode();
        hash = 31 * hash + rules.allowedTags().hashCode();
        hash = 31 * hash + rules.allowedNames().hashCode();
        hash = 31 * hash + rules.blockedMods().hashCode();
        hash = 31 * hash + rules.blockedTags().hashCode();
        hash = 31 * hash + rules.blockedNames().hashCode();
        return hash;
    }
}
