package com.terrasect.common.generation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public record Region(
    String name,
    int areaBudget, 
    Set<String> allowedStructures,
    Set<String> blockedStructures,
    Set<String> requiredStructures,
    Set<String> adjacentTo, 
    List<Region> children
) {
    public Region {
        if (allowedStructures == null) allowedStructures = Collections.emptySet();
        if (blockedStructures == null) blockedStructures = Collections.emptySet();
        if (requiredStructures == null) requiredStructures = Collections.emptySet();
        if (adjacentTo == null) adjacentTo = Collections.emptySet();
        if (children == null) children = Collections.emptyList();
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private Integer areaBudget = null;
        private Set<String> allowedStructures = Collections.emptySet();
        private Set<String> blockedStructures = Collections.emptySet();
        private Set<String> requiredStructures = Collections.emptySet();
        private Set<String> adjacentTo = Collections.emptySet();
        private List<Region> children = new ArrayList<>();

        public Builder(String name) {
            this.name = name;
        }

        public Builder budget(int areaBudget) {
            this.areaBudget = areaBudget;
            return this;
        }

        public Builder allow(String... structures) {
            this.allowedStructures = Set.of(structures);
            return this;
        }

        public Builder block(String... structures) {
            this.blockedStructures = Set.of(structures);
            return this;
        }

        public Builder require(String... structures) {
            this.requiredStructures = Set.of(structures);
            return this;
        }

        public Builder adjacentTo(String... regions) {
            this.adjacentTo = Set.of(regions);
            return this;
        }

        public Builder addChildren(Region... regions) {
            this.children = List.of(regions);
            return this;
        }

        public Region build() {
            int finalBudget;
            if (areaBudget != null) {
                finalBudget = areaBudget;
            } else if (!children.isEmpty()) {
                finalBudget = children.stream().mapToInt(Region::areaBudget).sum();
            } else {
                finalBudget = 10000;
            }
            return new Region(name, finalBudget, allowedStructures, blockedStructures, requiredStructures, adjacentTo, children);
        }
    }
}
