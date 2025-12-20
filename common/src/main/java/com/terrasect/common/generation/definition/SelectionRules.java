package com.terrasect.common.generation.definition;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Generic allow/block rules that can be applied to biomes, mobs, structures, or other identifiers.
 * Supports mixing mod namespaces, tags ("#namespace:tag"), and direct resource identifiers.
 * 
 * Optimized for fast lookups:
 * - Tags are pre-normalized (stored with and without # prefix)
 * - Uses HashSet for O(1) contains() checks
 * - Pre-computes hasAllowRules/hasBlockRules flags
 */
public record SelectionRules(
    Set<String> allowedMods,
    Set<String> allowedTags,      // Normalized: contains both "#tag" and "tag" forms
    Set<String> allowedNames,
    Set<String> blockedMods,
    Set<String> blockedTags,      // Normalized: contains both "#tag" and "tag" forms
    Set<String> blockedNames,
    boolean hasAllowRules,        // Pre-computed flag
    boolean hasBlockRules         // Pre-computed flag
) {
    public SelectionRules {
        if (allowedMods == null) allowedMods = Collections.emptySet();
        if (allowedTags == null) allowedTags = Collections.emptySet();
        if (allowedNames == null) allowedNames = Collections.emptySet();
        if (blockedMods == null) blockedMods = Collections.emptySet();
        if (blockedTags == null) blockedTags = Collections.emptySet();
        if (blockedNames == null) blockedNames = Collections.emptySet();
    }
    
    /**
     * Check if a biome name is explicitly allowed.
     */
    public boolean isNameAllowed(String biomeId) {
        return allowedNames.contains(biomeId);
    }
    
    /**
     * Check if a biome name is explicitly blocked.
     */
    public boolean isNameBlocked(String biomeId) {
        return blockedNames.contains(biomeId);
    }
    
    /**
     * Check if a mod namespace is allowed.
     */
    public boolean isModAllowed(String modNamespace) {
        return allowedMods.contains(modNamespace);
    }
    
    /**
     * Check if a mod namespace is blocked.
     */
    public boolean isModBlocked(String modNamespace) {
        return blockedMods.contains(modNamespace);
    }
    
    /**
     * Check if any of the given tags are in the allowed set.
     * Tags can be passed with or without # prefix - both will match.
     */
    public boolean hasAllowedTag(Set<String> biomeTags) {
        if (biomeTags == null || biomeTags.isEmpty() || allowedTags.isEmpty()) return false;
        for (String tag : biomeTags) {
            if (allowedTags.contains(tag)) return true;
        }
        return false;
    }
    
    /**
     * Check if any of the given tags are in the blocked set.
     * Tags can be passed with or without # prefix - both will match.
     */
    public boolean hasBlockedTag(Set<String> biomeTags) {
        if (biomeTags == null || biomeTags.isEmpty() || blockedTags.isEmpty()) return false;
        for (String tag : biomeTags) {
            if (blockedTags.contains(tag)) return true;
        }
        return false;
    }

    public static SelectionRules empty() {
        return builder().build();
    }

    public SelectionRules resolveWithParent(SelectionRules parent) {
        if (parent == null) return this;

        Set<String> mergedAllowedMods = merge(parent.allowedMods, allowedMods);
        Set<String> mergedAllowedTags = merge(parent.allowedTags, allowedTags);
        Set<String> mergedAllowedNames = merge(parent.allowedNames, allowedNames);

        Set<String> mergedBlockedMods = merge(parent.blockedMods, blockedMods);
        Set<String> mergedBlockedTags = merge(parent.blockedTags, blockedTags);
        Set<String> mergedBlockedNames = merge(parent.blockedNames, blockedNames);

        // Block lists override assumptions from the parent.
        mergedAllowedMods.removeAll(mergedBlockedMods);
        mergedAllowedTags.removeAll(mergedBlockedTags);
        mergedAllowedNames.removeAll(mergedBlockedNames);

        boolean hasAllow = !mergedAllowedMods.isEmpty() || !mergedAllowedTags.isEmpty() || !mergedAllowedNames.isEmpty();
        boolean hasBlock = !mergedBlockedMods.isEmpty() || !mergedBlockedTags.isEmpty() || !mergedBlockedNames.isEmpty();

        return new SelectionRules(
            Collections.unmodifiableSet(mergedAllowedMods),
            Collections.unmodifiableSet(mergedAllowedTags),
            Collections.unmodifiableSet(mergedAllowedNames),
            Collections.unmodifiableSet(mergedBlockedMods),
            Collections.unmodifiableSet(mergedBlockedTags),
            Collections.unmodifiableSet(mergedBlockedNames),
            hasAllow,
            hasBlock
        );
    }

    private Set<String> merge(Set<String> parent, Set<String> child) {
        Set<String> merged = new HashSet<>(Objects.requireNonNullElse(parent, Collections.emptySet()));
        merged.addAll(Objects.requireNonNullElse(child, Collections.emptySet()));
        return merged;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Set<String> allowedMods = new HashSet<>();
        private final Set<String> allowedTags = new HashSet<>();
        private final Set<String> allowedNames = new HashSet<>();
        private final Set<String> blockedMods = new HashSet<>();
        private final Set<String> blockedTags = new HashSet<>();
        private final Set<String> blockedNames = new HashSet<>();

        public Builder allowMods(String... mods) {
            Collections.addAll(allowedMods, mods);
            return this;
        }

        public Builder allowTags(String... tags) {
            for (String tag : tags) {
                // Store both normalized forms for fast lookup without allocation
                allowedTags.add(tag);
                if (tag.startsWith("#")) {
                    allowedTags.add(tag.substring(1));
                } else {
                    allowedTags.add("#" + tag);
                }
            }
            return this;
        }

        public Builder allowNames(String... names) {
            Collections.addAll(allowedNames, names);
            return this;
        }

        public Builder blockMods(String... mods) {
            Collections.addAll(blockedMods, mods);
            return this;
        }

        public Builder blockTags(String... tags) {
            for (String tag : tags) {
                // Store both normalized forms for fast lookup without allocation
                blockedTags.add(tag);
                if (tag.startsWith("#")) {
                    blockedTags.add(tag.substring(1));
                } else {
                    blockedTags.add("#" + tag);
                }
            }
            return this;
        }

        public Builder blockNames(String... names) {
            Collections.addAll(blockedNames, names);
            return this;
        }

        public SelectionRules build() {
            boolean hasAllow = !allowedMods.isEmpty() || !allowedTags.isEmpty() || !allowedNames.isEmpty();
            boolean hasBlock = !blockedMods.isEmpty() || !blockedTags.isEmpty() || !blockedNames.isEmpty();
            
            return new SelectionRules(
                Collections.unmodifiableSet(new HashSet<>(allowedMods)),
                Collections.unmodifiableSet(new HashSet<>(allowedTags)),
                Collections.unmodifiableSet(new HashSet<>(allowedNames)),
                Collections.unmodifiableSet(new HashSet<>(blockedMods)),
                Collections.unmodifiableSet(new HashSet<>(blockedTags)),
                Collections.unmodifiableSet(new HashSet<>(blockedNames)),
                hasAllow,
                hasBlock
            );
        }

        public Builder copyFrom(SelectionRules rules) {
            if (rules == null) return this;
            allowedMods.addAll(rules.allowedMods());
            allowedTags.addAll(rules.allowedTags());  // Already normalized
            allowedNames.addAll(rules.allowedNames());
            blockedMods.addAll(rules.blockedMods());
            blockedTags.addAll(rules.blockedTags());  // Already normalized
            blockedNames.addAll(rules.blockedNames());
            return this;
        }
    }
}
