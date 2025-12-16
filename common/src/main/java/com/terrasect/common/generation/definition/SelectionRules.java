package com.terrasect.common.generation.definition;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Generic allow/block rules that can be applied to biomes, mobs, structures, or other identifiers.
 * Supports mixing mod namespaces, tags ("#namespace:tag"), and direct resource identifiers.
 */
public record SelectionRules(
    Set<String> allowedMods,
    Set<String> allowedTags,
    Set<String> allowedNames,
    Set<String> blockedMods,
    Set<String> blockedTags,
    Set<String> blockedNames
) {
    public SelectionRules {
        if (allowedMods == null) allowedMods = Collections.emptySet();
        if (allowedTags == null) allowedTags = Collections.emptySet();
        if (allowedNames == null) allowedNames = Collections.emptySet();
        if (blockedMods == null) blockedMods = Collections.emptySet();
        if (blockedTags == null) blockedTags = Collections.emptySet();
        if (blockedNames == null) blockedNames = Collections.emptySet();
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

        return new SelectionRules(
            Collections.unmodifiableSet(mergedAllowedMods),
            Collections.unmodifiableSet(mergedAllowedTags),
            Collections.unmodifiableSet(mergedAllowedNames),
            Collections.unmodifiableSet(mergedBlockedMods),
            Collections.unmodifiableSet(mergedBlockedTags),
            Collections.unmodifiableSet(mergedBlockedNames)
        );
    }

    private Set<String> merge(Set<String> parent, Set<String> child) {
        Set<String> merged = new LinkedHashSet<>(Objects.requireNonNullElse(parent, Collections.emptySet()));
        merged.addAll(Objects.requireNonNullElse(child, Collections.emptySet()));
        return merged;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Set<String> allowedMods = new LinkedHashSet<>();
        private final Set<String> allowedTags = new LinkedHashSet<>();
        private final Set<String> allowedNames = new LinkedHashSet<>();
        private final Set<String> blockedMods = new LinkedHashSet<>();
        private final Set<String> blockedTags = new LinkedHashSet<>();
        private final Set<String> blockedNames = new LinkedHashSet<>();

        public Builder allowMods(String... mods) {
            Collections.addAll(allowedMods, mods);
            return this;
        }

        public Builder allowTags(String... tags) {
            Collections.addAll(allowedTags, tags);
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
            Collections.addAll(blockedTags, tags);
            return this;
        }

        public Builder blockNames(String... names) {
            Collections.addAll(blockedNames, names);
            return this;
        }

        public SelectionRules build() {
            return new SelectionRules(
                Collections.unmodifiableSet(new LinkedHashSet<>(allowedMods)),
                Collections.unmodifiableSet(new LinkedHashSet<>(allowedTags)),
                Collections.unmodifiableSet(new LinkedHashSet<>(allowedNames)),
                Collections.unmodifiableSet(new LinkedHashSet<>(blockedMods)),
                Collections.unmodifiableSet(new LinkedHashSet<>(blockedTags)),
                Collections.unmodifiableSet(new LinkedHashSet<>(blockedNames))
            );
        }

        public Builder copyFrom(SelectionRules rules) {
            if (rules == null) return this;
            allowMods(rules.allowedMods().toArray(String[]::new));
            allowTags(rules.allowedTags().toArray(String[]::new));
            allowNames(rules.allowedNames().toArray(String[]::new));
            blockMods(rules.blockedMods().toArray(String[]::new));
            blockTags(rules.blockedTags().toArray(String[]::new));
            blockNames(rules.blockedNames().toArray(String[]::new));
            return this;
        }
    }
}
