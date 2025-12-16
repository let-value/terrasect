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
    GenerationStrategyType generationStrategy
) {
    public RegionDefinition {
        if (climate == null) climate = ClimateSettings.empty();
        if (biomes == null) biomes = SelectionRules.empty();
        if (structures == null) structures = StructureRules.empty();
        if (mobs == null) mobs = SelectionRules.empty();
        if (generationStrategy == null) generationStrategy = GenerationStrategyType.VORONOI;
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
            generationStrategy
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ClimateSettings climate = ClimateSettings.empty();
        private SelectionRules biomes = SelectionRules.empty();
        private StructureRules structures = StructureRules.empty();
        private SelectionRules mobs = SelectionRules.empty();
        private GenerationStrategyType generationStrategy = GenerationStrategyType.VORONOI;

        public Builder climate(Consumer<ClimateSettings.Builder> consumer) {
            ClimateSettings.Builder builder = ClimateSettings.builder().copyFrom(climate);
            consumer.accept(builder);
            climate = builder.build();
            return this;
        }

        public Builder biomes(Consumer<SelectionRules.Builder> consumer) {
            SelectionRules.Builder builder = SelectionRules.builder().copyFrom(biomes);
            consumer.accept(builder);
            biomes = builder.build();
            return this;
        }

        public Builder structures(Consumer<StructureRules.Builder> consumer) {
            StructureRules.Builder builder = StructureRules.builder();
            builder.selection(SelectionRules.builder().copyFrom(structures.selection()).build());
            structures.requiredStructures().forEach(builder::requireStructures);
            consumer.accept(builder);
            structures = builder.build();
            return this;
        }

        public Builder mobs(Consumer<SelectionRules.Builder> consumer) {
            SelectionRules.Builder builder = SelectionRules.builder().copyFrom(mobs);
            consumer.accept(builder);
            mobs = builder.build();
            return this;
        }

        public Builder strategy(GenerationStrategyType generationStrategy) {
            this.generationStrategy = generationStrategy;
            return this;
        }

        public RegionDefinition build() {
            return new RegionDefinition(climate, biomes, structures, mobs, generationStrategy);
        }
    }
}
