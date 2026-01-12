package com.terrasect.common.generation;

import static com.terrasect.common.compat.ResourceKeyCompat.getKeyId;

import com.mojang.datafixers.util.Either;
import com.terrasect.common.Context;
import com.terrasect.common.lookup.BiomeLookup;
import com.terrasect.common.mixin.VanillaSamplerAccessor;
import com.terrasect.common.util.Packer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

public class MinecraftContext implements Context {

    private static final Map<ResourceKey<Level>, MinecraftContext> BY_DIMENSION = new ConcurrentHashMap<>();
    private static final Map<Climate.Sampler, MinecraftContext> BY_SAMPLER = new ConcurrentHashMap<>();

    private final long seed;
    private final String dimensionId;
    private final Climate.Sampler sampler;
    private final Climate.ParameterList<Holder<Biome>> parameterList;
    public final BiomeLookup biomeLookup;

    private MinecraftContext(
            long seed,
            String dimensionId,
            Climate.Sampler sampler,
            Climate.ParameterList<Holder<Biome>> parameterList,
            BiomeLookup biomeLookup) {
        this.seed = seed;
        this.dimensionId = dimensionId;
        this.sampler = sampler;
        this.parameterList = parameterList;
        this.biomeLookup = biomeLookup;
    }

    public static MinecraftContext create(
            ResourceKey<Level> dimension,
            long seed,
            Climate.Sampler sampler,
            Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters) {

        String dimensionId = getKeyId(dimension);
        Climate.ParameterList<Holder<Biome>> parameterList = parameters.map(list -> list, holder -> holder.value().parameters());

        BiomeLookup lookup = BiomeLookup.build(parameterList, dimensionId);

        MinecraftContext context = new MinecraftContext(seed, dimensionId, sampler, parameterList, lookup);

        BY_DIMENSION.put(dimension, context);
        if (sampler != null) {
            BY_SAMPLER.put(sampler, context);
        }

        World.initialize(context);

        return context;
    }

    public static MinecraftContext get(ResourceKey<Level> dimension) {
        return BY_DIMENSION.get(dimension);
    }

    public static MinecraftContext get(Climate.Sampler sampler) {
        MinecraftContext ctx = BY_SAMPLER.get(sampler);
        if (ctx != null) return ctx;

        ctx = BY_DIMENSION.get(Level.OVERWORLD);
        if (ctx != null) return ctx;

        return BY_DIMENSION.values().stream().findFirst().orElse(null);
    }

    public static void clear() {
        BY_DIMENSION.clear();
        BY_SAMPLER.clear();
    }

    @Override
    public long getSeed() {
        return seed;
    }

    public Climate.ParameterList<Holder<Biome>> parameterList() {
        return parameterList;
    }

    @Override
    public String getDimensionId() {
        return dimensionId;
    }

    @Override
    public long getInfluence(int x, int z) {
        if (sampler == null || parameterList == null) return 0L;

        Climate.TargetPoint target =
                ((VanillaSamplerAccessor) (Object) sampler).terrasect$sampleVanilla(x >> 2, 16, z >> 2);

        Holder<Biome> biome = parameterList.findValue(target);
        float river = biome.is(BiomeTags.IS_RIVER) ? 1.0f : 0.0f;

        long weirdness = target.weirdness();
        float normalized = (weirdness + 10000.0f) / 20000.0f;
        float ridge = Math.max(0.0f, Math.min(1.0f, normalized));

        return Packer.packPair(river, ridge);
    }
}
