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
    protected ClimateSettings.Builder climate;
    protected HeightConstraints.Builder height;
    protected NoiseConstraints.Builder noise;
    protected SelectionRules.Builder biomes;
    protected StructureRules.Builder structures;
    protected SelectionRules.Builder mobs;
    protected GenerationStrategy generationStrategy;

    protected abstract T self();

    public T climate(Consumer<ClimateSettings.Builder> consumer) {
      if (climate == null) {
        climate = ClimateSettings.builder();
      }
      consumer.accept(climate);
      return self();
    }

    public T height(Consumer<HeightConstraints.Builder> consumer) {
      if (height == null) {
        height = HeightConstraints.builder();
      }
      consumer.accept(height);
      return self();
    }

    public T noise(Consumer<NoiseConstraints.Builder> consumer) {
      if (noise == null) {
        noise = NoiseConstraints.builder();
      }
      consumer.accept(noise);
      return self();
    }

    public T biomes(Consumer<SelectionRules.Builder> consumer) {
      if (biomes == null) {
        biomes = SelectionRules.builder();
      }
      consumer.accept(biomes);
      return self();
    }

    public T structures(Consumer<StructureRules.Builder> consumer) {
      if (structures == null) {
        structures = StructureRules.builder();
      }
      consumer.accept(structures);
      return self();
    }

    public T mobs(Consumer<SelectionRules.Builder> consumer) {
      if (mobs == null) {
        mobs = SelectionRules.builder();
      }
      consumer.accept(mobs);
      return self();
    }

    public T strategy(GenerationStrategy generationStrategy) {
      this.generationStrategy = generationStrategy;
      return self();
    }

    public RegionDefinition build() {
      return new RegionDefinition(
          climate == null ? null : climate.build(),
          height == null ? null : height.build(),
          noise == null ? null : noise.build(),
          biomes == null ? null : biomes.build(),
          structures == null ? null : structures.buildRules(),
          mobs == null ? null : mobs.build(),
          generationStrategy);
    }
  }
}
