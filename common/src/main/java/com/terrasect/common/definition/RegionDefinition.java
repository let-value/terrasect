package com.terrasect.common.definition;

import java.util.function.Consumer;

public record RegionDefinition(
    ClimateSettings climate,
    HeightConstraints height,
    NoiseConstraints noise,
    SelectionRules biomes,
    StructureRules structures,
    SelectionRules mobs,
  GenerationStrategy generationStrategy) {
  public RegionDefinition {
    if (climate == null) climate = ClimateSettings.empty();
    if (height == null) height = HeightConstraints.empty();
    if (noise == null) noise = NoiseConstraints.empty();
    if (biomes == null) biomes = SelectionRules.empty();
    if (structures == null) structures = StructureRules.empty();
    if (mobs == null) mobs = SelectionRules.empty();
    if (generationStrategy == null) generationStrategy = GenerationStrategy.voronoi();
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
        generationStrategy);
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
    protected HeightConstraints height = HeightConstraints.empty();
    protected NoiseConstraints noise = NoiseConstraints.empty();
    protected SelectionRules biomes = SelectionRules.empty();
    protected StructureRules structures = StructureRules.empty();
    protected SelectionRules mobs = SelectionRules.empty();
    protected GenerationStrategy generationStrategy = GenerationStrategy.voronoi();

    protected abstract T self();

    public T climate(Consumer<ClimateSettings.Builder> consumer) {
      var builder = ClimateSettings.builder().copyFrom(climate);
      consumer.accept(builder);
      climate = builder.build();
      return self();
    }

    public T height(Consumer<HeightConstraints.Builder> consumer) {
      var builder = HeightConstraints.builder().copyFrom(height);
      consumer.accept(builder);
      height = builder.build();
      return self();
    }

    public T noise(Consumer<NoiseConstraints.Builder> consumer) {
      var builder = NoiseConstraints.builder().copyFrom(noise);
      consumer.accept(builder);
      noise = builder.build();
      return self();
    }

    public T biomes(Consumer<SelectionRules.Builder> consumer) {
      var builder = SelectionRules.builder().copyFrom(biomes);
      consumer.accept(builder);
      biomes = builder.build();
      return self();
    }

    public T structures(Consumer<StructureRules.Builder> consumer) {
      var builder = StructureRules.builder();
      builder.selection(structures.selection());
      structures.requiredStructures().forEach(builder::requireStructures);
      consumer.accept(builder);
      structures = builder.buildRules();
      return self();
    }

    public T mobs(Consumer<SelectionRules.Builder> consumer) {
      var builder = SelectionRules.builder().copyFrom(mobs);
      consumer.accept(builder);
      mobs = builder.build();
      return self();
    }

    public T strategy(GenerationStrategy generationStrategy) {
      this.generationStrategy = generationStrategy;
      return self();
    }

    public RegionDefinition build() {
      return new RegionDefinition(
          climate, height, noise, biomes, structures, mobs, generationStrategy);
    }
  }
}
