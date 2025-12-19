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
        if (rules == null) {
            return FilterResult.NO_RULES;
        }
        
        // Extract mod namespace from biome ID
        String modNamespace = extractNamespace(biomeId);
        
        // 1. Check explicit name rules first (highest priority)
        if (isAllowedByName(rules, biomeId)) {
            return FilterResult.ALLOWED;  // Explicit name allow overrides everything
        }
        if (isBlockedByName(rules, biomeId)) {
            return FilterResult.BLOCKED;  // Explicit name block overrides tag/mod rules
        }
        
        // 2. Check tag rules (medium priority)
        if (isBlockedByTag(rules, biomeTags)) {
            return FilterResult.BLOCKED;
        }
        
        // 3. Check mod rules (lowest priority)
        if (isBlockedByMod(rules, modNamespace)) {
            return FilterResult.BLOCKED;
        }
        
        // Check if any allow rules exist
        boolean hasAllowRules = !rules.allowedMods().isEmpty() 
            || !rules.allowedTags().isEmpty() 
            || !rules.allowedNames().isEmpty();
        
        if (!hasAllowRules) {
            // No allow rules = permissive, allow everything not blocked
            return FilterResult.NO_RULES;
        }
        
        // Check allow rules (name already checked above)
        if (isAllowedByTag(rules, biomeTags)) {
            return FilterResult.ALLOWED;
        }
        if (isAllowedByMod(rules, modNamespace)) {
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
     */
    public static boolean hasRules(SelectionRules rules) {
        if (rules == null) return false;
        return !rules.allowedMods().isEmpty()
            || !rules.allowedTags().isEmpty()
            || !rules.allowedNames().isEmpty()
            || !rules.blockedMods().isEmpty()
            || !rules.blockedTags().isEmpty()
            || !rules.blockedNames().isEmpty();
    }
    
    private static String extractNamespace(String resourceId) {
        if (resourceId == null) return "";
        int colonIndex = resourceId.indexOf(':');
        if (colonIndex > 0) {
            return resourceId.substring(0, colonIndex);
        }
        return "minecraft"; // Default namespace
    }
    
    private static boolean isBlockedByName(SelectionRules rules, String biomeId) {
        return rules.blockedNames().contains(biomeId);
    }
    
    private static boolean isBlockedByMod(SelectionRules rules, String modNamespace) {
        return rules.blockedMods().contains(modNamespace);
    }
    
    private static boolean isBlockedByTag(SelectionRules rules, Set<String> biomeTags) {
        if (biomeTags == null || biomeTags.isEmpty()) return false;
        for (String tag : biomeTags) {
            // Normalize tag format - rules may or may not have # prefix
            String normalizedTag = tag.startsWith("#") ? tag : "#" + tag;
            String tagWithoutHash = tag.startsWith("#") ? tag.substring(1) : tag;
            
            if (rules.blockedTags().contains(normalizedTag) 
                || rules.blockedTags().contains(tagWithoutHash)) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean isAllowedByName(SelectionRules rules, String biomeId) {
        return rules.allowedNames().contains(biomeId);
    }
    
    private static boolean isAllowedByMod(SelectionRules rules, String modNamespace) {
        return rules.allowedMods().contains(modNamespace);
    }
    
    private static boolean isAllowedByTag(SelectionRules rules, Set<String> biomeTags) {
        if (biomeTags == null || biomeTags.isEmpty()) return false;
        for (String tag : biomeTags) {
            // Normalize tag format
            String normalizedTag = tag.startsWith("#") ? tag : "#" + tag;
            String tagWithoutHash = tag.startsWith("#") ? tag.substring(1) : tag;
            
            if (rules.allowedTags().contains(normalizedTag) 
                || rules.allowedTags().contains(tagWithoutHash)) {
                return true;
            }
        }
        return false;
    }
    
    private BiomeFilter() {
        // Utility class
    }
}
