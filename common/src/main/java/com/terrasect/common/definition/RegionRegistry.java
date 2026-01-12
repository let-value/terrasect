package com.terrasect.common.definition;

import com.terrasect.common.Terrasect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class RegionRegistry {
    private final Map<String, DraftRegion> drafts = new LinkedHashMap<>();

    public DraftRegion region(String name) {
        return drafts.computeIfAbsent(name, n -> new DraftRegion(this, n));
    }

    public Region build(String rootName) {
        var rootDraft = drafts.get(rootName);
        if (rootDraft == null) {
            Terrasect.LOGGER.error("Unknown region root '{}'; returning empty region", rootName);
            return new Region(
                    rootName, 10000, RegionDefinition.empty(), Collections.emptySet(), List.of(), List.of(), false);
        }

        rootDraft.strategy(GenerationStrategy.hex());

        var childIndex = indexChildren();
        if (rootDraft.parent != null && !rootDraft.parent.isBlank()) {
            Terrasect.LOGGER.warn(
                    "Root region '{}' declared parent '{}'; ignoring parent assignment", rootName, rootDraft.parent);
            rootDraft.parent = null;
        }

        return buildResolved(rootName, RegionDefinition.empty(), childIndex, new HashSet<>());
    }

    public List<Region> buildAllRoots() {
        var childIndex = indexChildren();
        var roots = new ArrayList<Region>();
        var visiting = new HashSet<String>();
        drafts.values().stream()
                .filter(draft -> draft.parent == null || draft.parent.isBlank())
                .forEach(draft -> {
                    draft.strategy(GenerationStrategy.hex());
                    roots.add(buildResolved(draft.name, RegionDefinition.empty(), childIndex, visiting));
                });
        return roots;
    }

    private Map<String, List<String>> indexChildren() {
        var childIndex = new LinkedHashMap<String, List<String>>();
        drafts.values().forEach(draft -> {
            if (draft.parent != null && !draft.parent.isBlank()) {
                var parentDraft = drafts.get(draft.parent);
                if (parentDraft == null) {
                    Terrasect.LOGGER.error(
                            "Region '{}' references unknown parent '{}'; treating it as a root",
                            draft.name,
                            draft.parent);
                    draft.parent = null;
                    return;
                }
                childIndex
                        .computeIfAbsent(draft.parent, key -> new ArrayList<>())
                        .add(draft.name);
            }
        });
        return childIndex;
    }

    private Region buildResolved(
            String name, RegionDefinition inherited, Map<String, List<String>> childIndex, Set<String> visiting) {
        var draft = drafts.get(name);
        if (draft == null) {
            Terrasect.LOGGER.error("Missing draft for region '{}'; creating empty placeholder", name);
            return new Region(name, 10000, inherited, Collections.emptySet(), List.of(), List.of(), false);
        }

        if (!visiting.add(name)) {
            Terrasect.LOGGER.error("Region cycle detected at '{}'; truncating branch to avoid crash", name);
            return null;
        }

        var resolvedDefinition = draft.build().resolveInherited(inherited);

        List<Region> children = childIndex.getOrDefault(name, Collections.emptyList()).stream()
                .map(childName -> buildResolved(childName, resolvedDefinition, childIndex, visiting))
                .filter(Objects::nonNull)
                .toList();

        int budget;
        if (draft.areaBudget != null) {
            budget = draft.areaBudget;
        } else if (!children.isEmpty()) {
            budget = children.stream().mapToInt(Region::areaBudget).sum();
        } else {

            budget = 100 * 100;
        }

        visiting.remove(name);
        return new Region(
                name, budget, resolvedDefinition, draft.adjacentTo, children, List.of(), draft.anchoredToOrigin);
    }

    public static class DraftRegion extends RegionDefinition.AbstractBuilder<DraftRegion> {
        private final RegionRegistry registry;
        private final String name;
        private Integer areaBudget = null;
        private Set<String> adjacentTo = Collections.emptySet();
        private String parent = null;
        private boolean anchoredToOrigin = false;

        private DraftRegion(RegionRegistry registry, String name) {
            this.registry = registry;
            this.name = name;
        }

        @Override protected DraftRegion self() {
            return this;
        }

        public DraftRegion parent(String parentName) {
            if (this.parent != null && !this.parent.equals(parentName)) {
                Terrasect.LOGGER.warn(
                        "Region '{}' already has parent '{}'; ignoring new parent '{}'.",
                        this.name,
                        this.parent,
                        parentName);
                return this;
            }
            this.parent = parentName;
            return this;
        }

        public DraftRegion radius(int radius) {

            this.areaBudget = radius * radius;
            return this;
        }

        public DraftRegion adjacentTo(String... regions) {
            this.adjacentTo = Set.of(regions);
            return this;
        }

        public DraftRegion anchoredToOrigin() {
            this.anchoredToOrigin = true;
            return this;
        }

        public DraftRegion child(String childName, Consumer<DraftRegion> consumer) {
            var child = registry.region(childName);
            child.parent(this.name);
            consumer.accept(child);
            return this;
        }
    }
}
