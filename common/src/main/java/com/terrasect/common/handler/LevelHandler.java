package com.terrasect.common.handler;

import com.mojang.datafixers.util.Either;
import com.terrasect.common.generation.MinecraftContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

public final class LevelHandler {

    private LevelHandler() {}

    public static MinecraftContext registerContext(
            ResourceKey<Level> dimension,
            long seed,
            Climate.Sampler sampler,
            Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters) {

        return MinecraftContext.create(dimension, seed, sampler, parameters);
    }
}
