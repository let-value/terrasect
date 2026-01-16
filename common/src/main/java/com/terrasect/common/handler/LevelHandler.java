package com.terrasect.common.handler;

import com.mojang.datafixers.util.Either;
import com.terrasect.common.generation.MinecraftContext;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public final class LevelHandler {

  private LevelHandler() {}

  public static void registerContext(
      ResourceKey<Level> dimension,
      long seed,
      Climate.Sampler sampler,
      Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>>
          parameters,
      List<Holder<StructureSet>> possibleSets,
      RegistryAccess registryAccess) {

    MinecraftContext.create(dimension, seed, sampler, parameters, possibleSets, registryAccess);
  }
}
