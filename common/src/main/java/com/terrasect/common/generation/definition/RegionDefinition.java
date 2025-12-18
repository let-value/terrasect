package com.terrasect.common.generation.definition;

import java.util.function.Consumer;

/**
 * Narrative controls for a region. Everything here can be inherited and resolved ahead of time.
 */
public record RegionDefinition(
    ClimateSettings climate,
    SelectionRules biomes,
    StructureRules structures,
    SelectionRules mobs,
    GenerationStrategyType generationStrategy,
    StrategySettings strategySettings
) {
    public RegionDefinition {
        if (climate == null) climate = ClimateSettings.empty();
        if (biomes == null) biomes = SelectionRules.empty();
        if (structures == null) structures = StructureRules.empty();
        if (mobs == null) mobs = SelectionRules.empty();
        if (generationStrategy == null) generationStrategy = GenerationStrategyType.VORONOI;
        // strategySettings can be null - strategies use defaults
    }

    public static RegionDefinition empty() {
        return builder().build();
    }

    public RegionDefinition resolveInherited(RegionDefinition parent) {
        if (parent == null) return this;
        return new RegionDefinition(
            climate.resolveWithParent(parent.climate),
            biomes.resolveWithParent(parent.biomes),
            structures.resolveWithParent(parent.structures),
            mobs.resolveWithParent(parent.mobs),
            generationStrategy,
            strategySettings
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<Builder> {
        @Override
        protected Builder self() {
            return this;
        }
    }

    public abstract static class AbstractBuilder<T extends AbstractBuilder<T>> {
        protected ClimateSettings climate = ClimateSettings.empty();
        protected SelectionRules biomes = SelectionRules.empty();
        protected StructureRules structures = StructureRules.empty();
        protected SelectionRules mobs = SelectionRules.empty();
        protected GenerationStrategyType generationStrategy = GenerationStrategyType.VORONOI;
        protected StrategySettings strategySettings = null;

        protected abstract T self();

        public T climate(Consumer<ClimateSettings.Builder> consumer) {
            ClimateSettings.Builder builder = ClimateSettings.builder().copyFrom(climate);
            consumer.accept(builder);
            climate = builder.build();
            return self();
        }

        public T biomes(Consumer<SelectionRules.Builder> consumer) {
            SelectionRules.Builder builder = SelectionRules.builder().copyFrom(biomes);
            consumer.accept(builder);
            biomes = builder.build();
            return self();
        }

        public T structures(Consumer<StructureRules.Builder> consumer) {
            StructureRules.Builder builder = StructureRules.builder();
            builder.selection(SelectionRules.builder().copyFrom(structures.selection()).build());
            structures.requiredStructures().forEach(builder::requireStructures);
            consumer.accept(builder);
            structures = builder.build();
            return self();
        }

        public T mobs(Consumer<SelectionRules.Builder> consumer) {
            SelectionRules.Builder builder = SelectionRules.builder().copyFrom(mobs);
            consumer.accept(builder);
            mobs = builder.build();
            return self();
        }

        public T strategy(GenerationStrategyType generationStrategy) {
            this.generationStrategy = generationStrategy;
            return self();
        }

        public T settings(StrategySettings settings) {
            this.strategySettings = settings;
            return self();
        }

        /**
         * Configure settings using a builder.
         * Example: .settings(s -> s.ring("WILDERNESS").template(TemplateType.RADIAL))
         */
        public T settings(java.util.function.Consumer<StrategySettings.Builder> consumer) {
            StrategySettings.Builder builder = StrategySettings.builder();
            consumer.accept(builder);
            this.strategySettings = builder.build();
            return self();
        }

        public RegionDefinition build() {
            return new RegionDefinition(climate, biomes, structures, mobs, generationStrategy, strategySettings);
        }
    }
}
