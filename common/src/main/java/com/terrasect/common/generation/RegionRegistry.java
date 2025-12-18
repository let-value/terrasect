package com.terrasect.common.generation;

import com.terrasect.common.Terrasect;
import com.terrasect.common.generation.definition.ClimateSettings;
import com.terrasect.common.generation.definition.GenerationStrategyType;
import com.terrasect.common.generation.definition.RegionDefinition;
import com.terrasect.common.generation.definition.SelectionRules;
import com.terrasect.common.generation.definition.StructureRules;

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
 * <p>
 * Use {@link #region(String)} to get a {@link RegionRegistration} for configuring a region.
 * All properties (budget, adjacency, strategy, climate, biomes, structures, mobs) can be set
 * directly on the registration object using a fluent API.
 */
public class RegionRegistry {
    private final Map<String, RegionRegistration> registrations = new LinkedHashMap<>();

    public RegionRegistration region(String name) {
        return registrations.computeIfAbsent(name, n -> new RegionRegistration(this, n));
    }

    public Region build(String rootName) {
        RegionRegistration rootReg = registrations.get(rootName);
        if (rootReg == null) {
            Terrasect.LOGGER.error("Unknown region root '{}'; returning empty region", rootName);
            return new Region(rootName, 10000, RegionDefinition.empty(), Collections.emptySet(), List.of(), List.of());
        }

        rootReg.definitionBuilder.strategy(GenerationStrategyType.HEX);

        Map<String, List<String>> childIndex = indexChildren();
        if (rootReg.parent != null && !rootReg.parent.isBlank()) {
            Terrasect.LOGGER.warn("Root region '{}' declared parent '{}'; ignoring parent assignment", rootName, rootReg.parent);
            rootReg.parent = null;
        }

        return buildResolved(rootName, RegionDefinition.empty(), childIndex, new HashSet<>());
    }

    public List<Region> buildAllRoots() {
        Map<String, List<String>> childIndex = indexChildren();
        List<Region> roots = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        registrations.values().stream()
            .filter(reg -> reg.parent == null || reg.parent.isBlank())
            .forEach(reg -> {
                reg.definitionBuilder.strategy(GenerationStrategyType.HEX);
                roots.add(buildResolved(reg.name, RegionDefinition.empty(), childIndex, visiting));
            });
        return roots;
    }

    private Map<String, List<String>> indexChildren() {
        Map<String, List<String>> childIndex = new LinkedHashMap<>();
        registrations.values().forEach(reg -> {
            if (reg.parent != null && !reg.parent.isBlank()) {
                RegionRegistration parentReg = registrations.get(reg.parent);
                if (parentReg == null) {
                    Terrasect.LOGGER.error("Region '{}' references unknown parent '{}'; treating it as a root", reg.name, reg.parent);
                    reg.parent = null;
                    return;
                }
                childIndex.computeIfAbsent(reg.parent, key -> new ArrayList<>()).add(reg.name);
            }
        });
        return childIndex;
    }

    private Region buildResolved(String name, RegionDefinition inherited, Map<String, List<String>> childIndex, Set<String> visiting) {
        RegionRegistration reg = registrations.get(name);
        if (reg == null) {
            Terrasect.LOGGER.error("Missing registration for region '{}'; creating empty placeholder", name);
            return new Region(name, 10000, inherited, Collections.emptySet(), List.of(), List.of());
        }

        if (!visiting.add(name)) {
            Terrasect.LOGGER.error("Region cycle detected at '{}'; truncating branch to avoid crash", name);
            return null;
        }

        RegionDefinition resolvedDefinition = reg.definitionBuilder.build().resolveInherited(inherited);

        List<Region> children = childIndex.getOrDefault(name, Collections.emptyList()).stream()
            .map(childName -> buildResolved(childName, resolvedDefinition, childIndex, visiting))
            .filter(Objects::nonNull)
            .toList();

        int budget;
        if (reg.areaBudget != null) {
            budget = reg.areaBudget;
        } else if (!children.isEmpty()) {
            budget = children.stream().mapToInt(Region::areaBudget).sum();
        } else {
            budget = 10000;
        }

        visiting.remove(name);
        return new Region(name, budget, resolvedDefinition, reg.adjacentTo, children, List.of());
    }

    /**
     * Fluent registration for configuring a region's properties.
     * All region properties can be set directly without needing intermediate classes.
     */
    public static class RegionRegistration {
        private final RegionRegistry registry;
        final String name;
        Integer areaBudget = null;
        final RegionDefinition.Builder definitionBuilder = RegionDefinition.builder();
        Set<String> adjacentTo = Collections.emptySet();
        String parent = null;

        private RegionRegistration(RegionRegistry registry, String name) {
            this.registry = registry;
            this.name = name;
        }

        public RegionRegistration parent(String parentName) {
            if (this.parent != null && !this.parent.equals(parentName)) {
                Terrasect.LOGGER.warn("Region '{}' already has parent '{}'; ignoring new parent '{}'.", this.name, this.parent, parentName);
                return this;
            }
            this.parent = parentName;
            return this;
        }

        public RegionRegistration budget(int areaBudget) {
            this.areaBudget = areaBudget;
            return this;
        }

        public RegionRegistration definition(Consumer<RegionDefinition.Builder> consumer) {
            consumer.accept(this.definitionBuilder);
            return this;
        }

        public RegionRegistration adjacentTo(String... regions) {
            this.adjacentTo = Set.of(regions);
            return this;
        }

        public RegionRegistration child(String childName, Consumer<RegionRegistration> consumer) {
            RegionRegistration child = registry.region(childName);
            child.parent(this.name);
            consumer.accept(child);
            return this;
        }

        // ========== Convenience methods for direct property configuration ==========

        /**
         * Set the generation strategy for this region directly.
         */
        public RegionRegistration strategy(GenerationStrategyType strategyType) {
            this.definitionBuilder.strategy(strategyType);
            return this;
        }

        /**
         * Configure climate settings for this region directly.
         */
        public RegionRegistration climate(Consumer<ClimateSettings.Builder> consumer) {
            this.definitionBuilder.climate(consumer);
            return this;
        }

        /**
         * Configure biome selection rules for this region directly.
         */
        public RegionRegistration biomes(Consumer<SelectionRules.Builder> consumer) {
            this.definitionBuilder.biomes(consumer);
            return this;
        }

        /**
         * Configure structure rules for this region directly.
         */
        public RegionRegistration structures(Consumer<StructureRules.Builder> consumer) {
            this.definitionBuilder.structures(consumer);
            return this;
        }

        /**
         * Configure mob selection rules for this region directly.
         */
        public RegionRegistration mobs(Consumer<SelectionRules.Builder> consumer) {
            this.definitionBuilder.mobs(consumer);
            return this;
        }
    }
}
