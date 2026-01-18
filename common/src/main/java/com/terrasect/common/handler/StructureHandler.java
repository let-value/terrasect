package com.terrasect.common.handler;

import com.terrasect.common.definition.StructureRules;
import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.generation.World;
import com.terrasect.common.lookup.StructureSetsLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Climate;

public final class StructureHandler {

  private StructureHandler() {}

  public static StructureSetsLookup.FilteredSets getFilteredSets(
      ResourceKey<Level> dimension, ChunkPos chunkPos) {
    return getFilteredSets(MinecraftContext.get(dimension), chunkPos);
  }

  public static StructureSetsLookup.FilteredSets getFilteredSets(
      Climate.Sampler sampler, ChunkPos chunkPos) {
    return getFilteredSets(MinecraftContext.get(sampler), chunkPos);
  }

  private static StructureSetsLookup.FilteredSets getFilteredSets(
      MinecraftContext context, ChunkPos chunkPos) {
    if (context == null) {
      return null;
    }

    var lookup = context.structureSetsLookup;
    if (lookup == null) {
      return null;
    }

    var rules = resolveRules(context, chunkPos);
    return lookup.getSets(rules);
  }

  private static StructureRules resolveRules(MinecraftContext context, ChunkPos chunkPos) {
    var traversal =
        World.traverse(context, chunkPos.getMinBlockX() + 8, chunkPos.getMinBlockZ() + 8);
    if (traversal == null || traversal.region == null) {
      return null;
    }

    var rules = traversal.region.definition().structures();
    return rules != null && rules.hasFilters() ? rules : null;
  }
}
