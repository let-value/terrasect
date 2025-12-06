package com.terrasect.worldgen;

import com.mojang.serialization.MapCodec;
import com.terrasect.Terrasect;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

/**
 * Custom BiomeSource that returns a single biome for all positions
 * This is the key component that makes the world generation use only one biome
 */
public class SingleBiomeSource extends BiomeSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleBiomeSource.class);
    
    public static final MapCodec<SingleBiomeSource> CODEC = MapCodec.unit(() -> {
        LOGGER.info("Creating SingleBiomeSource codec");
        return new SingleBiomeSource();
    });
    
    private Holder<Biome> biome;
    
    public SingleBiomeSource() {
        LOGGER.info("SingleBiomeSource instantiated");
    }
    
    public void setBiome(Holder<Biome> biome) {
        this.biome = biome;
        LOGGER.info("SingleBiomeSource set to biome: {}", biome);
    }
    
    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }
    
    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biome != null ? Stream.of(biome) : Stream.empty();
    }
    
    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        // Return the same biome for all coordinates
        return biome;
    }
}
