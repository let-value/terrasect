package com.terrasect.common.handler;

import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.generation.World;
import com.terrasect.common.helpers.BiomeFilter;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

public final class BiomeHandler {

  private BiomeHandler() {}

  public static Holder<Biome> selectBiome(
      MinecraftContext context, int quartX, int quartZ, Climate.TargetPoint targetPoint) {

    var blockX = quartX << 2;
    var blockZ = quartZ << 2;

    var traversal = World.traverse(context, blockX, blockZ);
    var region = traversal != null ? traversal.region : null;

    if (region == null) {
      return selectFromBaseList(context, targetPoint);
    }

    var rules = region.definition().biomes();
    if (!BiomeFilter.hasRules(rules)) {
      return selectFromBaseList(context, targetPoint);
    }

    var filteredList = context.biomeLookup.getFilteredParameterList(rules);
    if (filteredList == null) {
      return selectFromBaseList(context, targetPoint);
    }

    return filteredList.findValue(targetPoint);
  }

  private static Holder<Biome> selectFromBaseList(
      MinecraftContext context, Climate.TargetPoint targetPoint) {
    return context.parameterList().findValue(targetPoint);
  }
}
