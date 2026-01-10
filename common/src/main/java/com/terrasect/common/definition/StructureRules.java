package com.terrasect.common.definition;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record StructureRules(SelectionRules selection, Set<String> requiredStructures) {
    public StructureRules {
        if (selection == null) selection = SelectionRules.empty();
        if (requiredStructures == null) requiredStructures = Collections.emptySet();
    }

    public static StructureRules empty() {
        return builder().build();
    }

    public StructureRules resolveWithParent(StructureRules parent) {
        if (parent == null) return this;

        SelectionRules mergedSelection = selection.resolveWithParent(parent.selection);
        Set<String> mergedRequired = new LinkedHashSet<>(parent.requiredStructures);
        mergedRequired.addAll(requiredStructures);
        mergedRequired.removeAll(mergedSelection.blockedNames());

        return new StructureRules(mergedSelection, Collections.unmodifiableSet(mergedRequired));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private SelectionRules selection = SelectionRules.empty();
        private final Set<String> requiredStructures = new LinkedHashSet<>();

        public Builder selection(SelectionRules selection) {
            this.selection = selection;
            return this;
        }

        public Builder selection(SelectionRules.Builder builder) {
            this.selection = builder.build();
            return this;
        }

        public Builder allowMods(String... mods) {
            SelectionRules.Builder builder = SelectionRules.builder().copyFrom(selection);
            builder.allowMods(mods);
            selection = builder.build();
            return this;
        }

        public Builder allowTags(String... tags) {
            SelectionRules.Builder builder = SelectionRules.builder().copyFrom(selection);
            builder.allowTags(tags);
            selection = builder.build();
            return this;
        }

        public Builder allowNames(String... names) {
            SelectionRules.Builder builder = SelectionRules.builder().copyFrom(selection);
            builder.allowNames(names);
            selection = builder.build();
            return this;
        }

        public Builder blockMods(String... mods) {
            SelectionRules.Builder builder = SelectionRules.builder().copyFrom(selection);
            builder.blockMods(mods);
            selection = builder.build();
            return this;
        }

        public Builder blockTags(String... tags) {
            SelectionRules.Builder builder = SelectionRules.builder().copyFrom(selection);
            builder.blockTags(tags);
            selection = builder.build();
            return this;
        }

        public Builder blockNames(String... names) {
            SelectionRules.Builder builder = SelectionRules.builder().copyFrom(selection);
            builder.blockNames(names);
            selection = builder.build();
            return this;
        }

        public Builder requireStructures(String... structures) {
            Collections.addAll(requiredStructures, structures);
            return this;
        }

        public StructureRules build() {
            return new StructureRules(selection, Collections.unmodifiableSet(new LinkedHashSet<>(requiredStructures)));
        }
    }
}
