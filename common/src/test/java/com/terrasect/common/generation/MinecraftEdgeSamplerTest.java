package com.terrasect.common.generation;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftEdgeSamplerTest {

    @BeforeAll
    static void setupMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void collectsMultiScaleEdgeStatisticsFromOverworldSlice() {
        long seed = 12345L;
        HolderLookup.Provider lookup = VanillaRegistries.createLookup();
        HolderGetter<NormalNoise.NoiseParameters> noiseParams = lookup.lookupOrThrow(Registries.NOISE);

        NoiseGeneratorSettings settings;
        try {
            settings = lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        } catch (Exception e) {
            settings = NoiseGeneratorSettings.dummy();
        }

        RandomState randomState = RandomState.create(settings, noiseParams, seed);
        var parameterListLookup = lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var overworldParameters = parameterListLookup.getOrThrow(net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        MultiNoiseBiomeSource biomeSource = MultiNoiseBiomeSource.createFromPreset(overworldParameters);

        MinecraftEdgeSampler sampler = new MinecraftEdgeSampler(biomeSource, randomState.sampler());
        MinecraftEdgeSampler.EdgeStatistics fineStatistics = sampler.sampleArea(0, 0, 512, 4);
        MinecraftEdgeSampler.EdgeStatistics coarseStatistics = sampler.sampleArea(0, 0, 2048, 32);

        System.out.printf("Fine: density=%.4f jitter=(h=%.2f,v=%.2f) climate dT=%.4f dW=%.4f%n",
            fineStatistics.roughness().transitionDensity(),
            fineStatistics.roughness().meanHorizontalJitter(),
            fineStatistics.roughness().meanVerticalJitter(),
            fineStatistics.climateEdgeDeltas().averageTemperatureDelta(),
            fineStatistics.climateEdgeDeltas().averageWeirdnessDelta());
        System.out.printf("Coarse: density=%.4f jitter=(h=%.2f,v=%.2f) avg run blocks=%.2f%n",
            coarseStatistics.roughness().transitionDensity(),
            coarseStatistics.roughness().meanHorizontalJitter(),
            coarseStatistics.roughness().meanVerticalJitter(),
            coarseStatistics.averageRunLengthBlocks());

        assertTrue(fineStatistics.distinctBiomes() >= 8, "expected a diverse biome mix in overworld slice");
        assertTrue(fineStatistics.transitionTotal() > 0, "should have plenty of biome transitions at chunk scale");
        assertTrue(fineStatistics.averageRunLength() > 1.5 && fineStatistics.averageRunLength() < 64,
            "run lengths should capture small and mid-scale striping");

        assertTrue(fineStatistics.roughness().transitionDensity() > 0.02,
            "biome noise should produce frequent edge crossings");
        assertTrue(fineStatistics.roughness().meanHorizontalJitter() < 48,
            "edge jitter should be bounded at block-scale detail");

        assertTrue(coarseStatistics.roughness().transitionDensity() > 0.005,
            "coarse sampling should still capture macro transitions");
        assertTrue(coarseStatistics.averageRunLengthBlocks() >= fineStatistics.averageRunLengthBlocks(),
            "coarser sampling should yield longer stretches in world distance");
        assertTrue(coarseStatistics.roughness().meanVerticalJitter() >= 0,
            "jitter metric should remain non-negative at all scales");

        assertTrue(fineStatistics.climateEdgeDeltas().hasSamples(), "should have climate deltas recorded along edges");
        assertTrue(fineStatistics.climateEdgeDeltas().averageTemperatureDelta() > 0.01,
            "temperature should vary at biome edges");
        assertTrue(fineStatistics.climateEdgeDeltas().averageWeirdnessDelta() > 0.01,
            "weirdness should contribute to vanilla edge shaping");

        String riverKey = "minecraft:river";
        if (fineStatistics.biomeCounts().containsKey(riverKey)) {
            assertTrue(fineStatistics.transitionCounts().keySet().stream().anyMatch(key -> key.contains(riverKey)),
                "river biomes should interact with neighboring edges");
        }
    }
}
