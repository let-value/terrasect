package com.terrasect.fabric;

import com.terrasect.common.generation.Region;
import com.terrasect.common.generation.Strategy;
import com.terrasect.common.generation.World;
import com.terrasect.common.generation.TestRegions;
import com.terrasect.common.generation.mixin.ClimateHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import org.junit.jupiter.api.Assertions;

import java.util.HashMap;
import java.util.Map;

public class GameTestIntegration {

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "terrasect")
    public void testRegionDistribution(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestRegions.register();

        Strategy context = new Strategy() {
            @Override
            public long getSeed() { return level.getSeed(); }
            @Override
            public float getRiverInfluence(int x, int z) { return 0.0f; }
            @Override
            public float getRidgeInfluence(int x, int z) { return 0.5f; }
        };

        Map<String, Integer> regionCounts = new HashMap<>();
        int modified = 0;
        int total = 0;

        for (int x = -512; x <= 512; x += 32) {
            for (int z = -512; z <= 512; z += 32) {
                Region region = World.getRegion(x, z, context);
                String name = region != null ? region.name() : "null";
                regionCounts.merge(name, 1, Integer::sum);

                // Sample climate modification
                ClimateHandler.ClimateResult result = ClimateHandler.modifyClimate(
                    context, x, 64, z, 0.5f, 0.5f);
                total++;
                if (result.modified()) modified++;
            }
        }

        System.out.println("GameTest region distribution:");
        regionCounts.forEach((name, count) -> System.out.println("  " + name + ": " + count));
        System.out.println("Climate modification rate: " + (100.0 * modified / total) + "%");

        // Assert at least 3 regions found and some modifications
        Assertions.assertTrue(regionCounts.size() >= 3, "Should find at least 3 regions");
        Assertions.assertTrue(modified > 0, "Should modify some climates");
        helper.succeed();
    }
}
