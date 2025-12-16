package com.terrasect.common.generation;

import com.terrasect.common.Terrasect;
import com.terrasect.common.generation.definition.GenerationStrategyType;
import com.terrasect.common.generation.definition.RegionDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Collects region definitions before baking them into a fully resolved tree in a single pass.
 * Supports both nested (declarative) and flat (parent reference) registration styles.
 */
public class RegionRegistry {
    private final Map<String, DraftRegion> drafts = new LinkedHashMap<>();

    public RegionRegistration region(String name) {
        return drafts.computeIfAbsent(name, DraftRegion::new).registration(this);
    }

    public Region build(String rootName) {
        DraftRegion rootDraft = drafts.get(rootName);
        if (rootDraft == null) {
            Terrasect.LOGGER.error("Unknown region root '{}'; returning empty region", rootName);
            return new Region(rootName, 10000, RegionDefinition.empty(), Collections.emptySet(), List.of());
        }

        rootDraft.definitionBuilder.strategy(GenerationStrategyType.HEX);

        Map<String, List<String>> childIndex = indexChildren();
        if (rootDraft.parent != null && !rootDraft.parent.isBlank()) {
            Terrasect.LOGGER.warn("Root region '{}' declared parent '{}'; ignoring parent assignment", rootName, rootDraft.parent);
            rootDraft.parent = null;
        }

        return buildResolved(rootName, RegionDefinition.empty(), childIndex, new HashSet<>());
    }

    public List<Region> buildAllRoots() {
        Map<String, List<String>> childIndex = indexChildren();
        List<Region> roots = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        drafts.values().stream()
            .filter(draft -> draft.parent == null || draft.parent.isBlank())
            .forEach(draft -> {
                draft.definitionBuilder.strategy(GenerationStrategyType.HEX);
                roots.add(buildResolved(draft.name, RegionDefinition.empty(), childIndex, visiting));
            });
        return roots;
    }

    private Map<String, List<String>> indexChildren() {
        Map<String, List<String>> childIndex = new LinkedHashMap<>();
        drafts.values().forEach(draft -> {
            if (draft.parent != null && !draft.parent.isBlank()) {
                DraftRegion parentDraft = drafts.get(draft.parent);
                if (parentDraft == null) {
                    Terrasect.LOGGER.error("Region '{}' references unknown parent '{}'; treating it as a root", draft.name, draft.parent);
                    draft.parent = null;
                    return;
                }
                childIndex.computeIfAbsent(draft.parent, key -> new ArrayList<>()).add(draft.name);
            }
        });
        return childIndex;
    }

    private Region buildResolved(String name, RegionDefinition inherited, Map<String, List<String>> childIndex, Set<String> visiting) {
        DraftRegion draft = drafts.get(name);
        if (draft == null) {
            Terrasect.LOGGER.error("Missing draft for region '{}'; creating empty placeholder", name);
            return new Region(name, 10000, inherited, Collections.emptySet(), List.of());
        }

        if (!visiting.add(name)) {
            Terrasect.LOGGER.error("Region cycle detected at '{}'; truncating branch to avoid crash", name);
            return null;
        }

        RegionDefinition resolvedDefinition = draft.definitionBuilder.build().resolveInherited(inherited);

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
            budget = 10000;
        }

        visiting.remove(name);
        return new Region(name, budget, resolvedDefinition, draft.adjacentTo, children);
    }

    private static class DraftRegion {
        private final String name;
        private Integer areaBudget = null;
        private final RegionDefinition.Builder definitionBuilder = RegionDefinition.builder();
        private Set<String> adjacentTo = Collections.emptySet();
        private String parent = null;

        private DraftRegion(String name) {
            this.name = name;
        }

        private RegionRegistration registration(RegionRegistry registry) {
            return new RegionRegistration(registry, this);
        }
    }

    public static class RegionRegistration {
        private final RegionRegistry registry;
        private final DraftRegion draft;

        private RegionRegistration(RegionRegistry registry, DraftRegion draft) {
            this.registry = registry;
            this.draft = draft;
        }

        public RegionRegistration parent(String parentName) {
            if (draft.parent != null && !draft.parent.equals(parentName)) {
                Terrasect.LOGGER.warn("Region '{}' already has parent '{}'; ignoring new parent '{}'.", draft.name, draft.parent, parentName);
                return this;
            }
            draft.parent = parentName;
            return this;
        }

        public RegionRegistration budget(int areaBudget) {
            draft.areaBudget = areaBudget;
            return this;
        }

        public RegionRegistration definition(Consumer<RegionDefinition.Builder> consumer) {
            consumer.accept(draft.definitionBuilder);
            return this;
        }

        public RegionRegistration adjacentTo(String... regions) {
            draft.adjacentTo = Set.of(regions);
            return this;
        }

        public RegionRegistration child(String childName, Consumer<RegionRegistration> consumer) {
            RegionRegistration child = registry.region(childName);
            child.parent(draft.name);
            consumer.accept(child);
            return this;
        }
    }
}
