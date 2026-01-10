package com.terrasect.common.definition;

import java.util.function.Consumer;

public record RegionDefinition(
        ClimateSettings climate,
        HeightConstraints height,
        NoiseConstraints noise,
        SelectionRules biomes,
        StructureRules structures,
        SelectionRules mobs,
        GenerationStrategyType generationStrategy,
        StrategySettings strategySettings) {
    public RegionDefinition {
        if (climate == null) climate = ClimateSettings.empty();
        if (height == null) height = HeightConstraints.inherit();
        if (noise == null) noise = NoiseConstraints.empty();
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
                height.resolveWithParent(parent.height),
                noise.resolveWithParent(parent.noise),
                biomes.resolveWithParent(parent.biomes),
                structures.resolveWithParent(parent.structures),
                mobs.resolveWithParent(parent.mobs),
                generationStrategy,
                strategySettings);
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
        protected HeightConstraints height = HeightConstraints.inherit();
        protected NoiseConstraints noise = NoiseConstraints.empty();
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

        public T height(int minY, int maxY) {
            this.height = HeightConstraints.range(minY, maxY);
            return self();
        }

        public T height(int y) {
            this.height = HeightConstraints.exact(y);
            return self();
        }

        public T noHeightConstraints() {
            this.height = HeightConstraints.unconstrained();
            return self();
        }

        public T noise(java.util.function.Consumer<NoiseConstraints.Builder> consumer) {
            NoiseConstraints.Builder builder = NoiseConstraints.builder().copyFrom(noise);
            consumer.accept(builder);
            noise = builder.build();
            return self();
        }

        public T noNoiseConstraints() {
            this.noise = NoiseConstraints.clearParent();
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
            builder.selection(
                    SelectionRules.builder().copyFrom(structures.selection()).build());
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

        public T settings(java.util.function.Consumer<StrategySettings.Builder> consumer) {
            StrategySettings.Builder builder = StrategySettings.builder();
            consumer.accept(builder);
            this.strategySettings = builder.build();
            return self();
        }

        public RegionDefinition build() {
            return new RegionDefinition(
                    climate, height, noise, biomes, structures, mobs, generationStrategy, strategySettings);
        }
    }
}
