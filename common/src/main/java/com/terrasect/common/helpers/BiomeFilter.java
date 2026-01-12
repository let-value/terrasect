package com.terrasect.common.helpers;

import com.terrasect.common.definition.SelectionRules;
import java.util.Set;

public final class BiomeFilter {

    public enum FilterResult {
        ALLOWED,

        BLOCKED,

        NO_RULES
    }

    public static FilterResult checkBiome(SelectionRules rules, String biomeId, Set<String> biomeTags) {
        if (rules == null || (!rules.hasAllowRules() && !rules.hasBlockRules())) {
            return FilterResult.NO_RULES;
        }

        if (rules.isNameAllowed(biomeId)) {
            return FilterResult.ALLOWED;
        }
        if (rules.isNameBlocked(biomeId)) {
            return FilterResult.BLOCKED;
        }

        if (rules.hasBlockedTag(biomeTags)) {
            return FilterResult.BLOCKED;
        }

        String modNamespace = extractNamespace(biomeId);
        if (rules.isModBlocked(modNamespace)) {
            return FilterResult.BLOCKED;
        }

        if (!rules.hasAllowRules()) {
            return FilterResult.NO_RULES;
        }

        if (rules.hasAllowedTag(biomeTags)) {
            return FilterResult.ALLOWED;
        }
        if (rules.isModAllowed(modNamespace)) {
            return FilterResult.ALLOWED;
        }

        return FilterResult.BLOCKED;
    }

    public static boolean isAllowed(SelectionRules rules, String biomeId, Set<String> biomeTags) {
        FilterResult result = checkBiome(rules, biomeId, biomeTags);
        return result != FilterResult.BLOCKED;
    }

    public static boolean hasRules(SelectionRules rules) {
        if (rules == null) return false;
        return rules.hasAllowRules() || rules.hasBlockRules();
    }

    private static String extractNamespace(String resourceId) {
        if (resourceId == null || resourceId.isEmpty()) return "minecraft";
        var colonIndex = resourceId.indexOf(':');
        if (colonIndex > 0) {
            return resourceId.substring(0, colonIndex);
        }
        return "minecraft";
    }

    private BiomeFilter() {
    }
}
