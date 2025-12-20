package com.terrasect.common.generation;

import com.terrasect.common.generation.definition.SelectionRules;

import java.util.Set;

/**
 * Filters biomes based on region's SelectionRules.
 * 
 * This class provides loader-agnostic logic for checking whether a biome
 * should be allowed or blocked based on:
 * - Mod namespace (e.g., "minecraft", "terralith")
 * - Tags (e.g., "#minecraft:is_forest", "#c:climate_hot")
 * - Direct biome names (e.g., "minecraft:plains", "terralith:yellowstone")
 * 
 * The filtering logic:
 * 1. If blocked by name, mod, or tag -> BLOCKED
 * 2. If no allow rules exist -> ALLOWED (permissive by default)
 * 3. If allowed by name, mod, or tag -> ALLOWED
 * 4. Otherwise -> BLOCKED (if allow rules exist but don't match)
 * 
 * Performance optimizations:
 * - No string allocations in hot path (tag normalization done at construction)
 * - O(1) HashSet lookups instead of iteration
 * - Pre-computed hasAllowRules/hasBlockRules flags
 * - Single-pass algorithm with early exits
 */
public final class BiomeFilter {

    /**
     * Result of biome filtering check.
     */
    public enum FilterResult {
        /** Biome is allowed by the rules */
        ALLOWED,
        /** Biome is explicitly blocked */
        BLOCKED,
        /** No rules apply - use default behavior */
        NO_RULES
    }

    /**
     * Check if a biome is allowed based on the selection rules.
     * 
     * Priority order:
     * 1. Explicit name allow/block (most specific, highest priority)
     * 2. Tag allow/block
     * 3. Mod allow/block (least specific)
     * 
     * @param rules The selection rules from the region definition
     * @param biomeId Full biome identifier (e.g., "minecraft:plains")
     * @param biomeTags Set of tags this biome has (e.g., ["#minecraft:is_overworld", "#c:climate_temperate"])
     * @return FilterResult indicating if biome is allowed, blocked, or no rules apply
     */
    public static FilterResult checkBiome(SelectionRules rules, String biomeId, Set<String> biomeTags) {
        if (rules == null || (!rules.hasAllowRules() && !rules.hasBlockRules())) {
            return FilterResult.NO_RULES;
        }
        
        // 1. Check explicit name rules first (highest priority)
        if (rules.isNameAllowed(biomeId)) {
            return FilterResult.ALLOWED;  // Explicit name allow overrides everything
        }
        if (rules.isNameBlocked(biomeId)) {
            return FilterResult.BLOCKED;  // Explicit name block overrides tag/mod rules
        }
        
        // 2. Check tag rules (medium priority) - no allocation, direct HashSet lookup
        if (rules.hasBlockedTag(biomeTags)) {
            return FilterResult.BLOCKED;
        }
        
        // 3. Check mod rules (lowest priority)
        // Extract namespace without allocation when possible
        String modNamespace = extractNamespace(biomeId);
        if (rules.isModBlocked(modNamespace)) {
            return FilterResult.BLOCKED;
        }
        
        // No allow rules = permissive, allow everything not blocked
        if (!rules.hasAllowRules()) {
            return FilterResult.NO_RULES;
        }
        
        // Check allow rules (name already checked above)
        if (rules.hasAllowedTag(biomeTags)) {
            return FilterResult.ALLOWED;
        }
        if (rules.isModAllowed(modNamespace)) {
            return FilterResult.ALLOWED;
        }
        
        // Allow rules exist but don't match - block
        return FilterResult.BLOCKED;
    }
    
    /**
     * Simple check that returns true if allowed, false if blocked.
     * For cases where you just need a boolean result.
     */
    public static boolean isAllowed(SelectionRules rules, String biomeId, Set<String> biomeTags) {
        FilterResult result = checkBiome(rules, biomeId, biomeTags);
        return result != FilterResult.BLOCKED;
    }
    
    /**
     * Check if rules have any biome constraints defined.
     * Uses pre-computed flags for O(1) check.
     */
    public static boolean hasRules(SelectionRules rules) {
        if (rules == null) return false;
        return rules.hasAllowRules() || rules.hasBlockRules();
    }
    
    /**
     * Extract namespace from resource ID without allocation when colon is not found.
     */
    private static String extractNamespace(String resourceId) {
        if (resourceId == null || resourceId.isEmpty()) return "minecraft";
        int colonIndex = resourceId.indexOf(':');
        if (colonIndex > 0) {
            return resourceId.substring(0, colonIndex);
        }
        return "minecraft"; // Default namespace
    }
    
    private BiomeFilter() {
        // Utility class
    }
}
