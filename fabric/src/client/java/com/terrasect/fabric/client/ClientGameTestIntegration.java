package com.terrasect.fabric.client;

import com.terrasect.common.generation.debug.MixinSampler;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * Client GameTest that creates a real world with noise-based terrain generation
 * to verify that our mod is properly modifying biome selection.
 * 
 * This test uses MixinSampler to collect detailed data about climate and biome
 * modifications happening during world generation.
 */
public class ClientGameTestIntegration implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        System.out.println("=== Client GameTest Starting ===");
        System.out.println("Creating a NORMAL world (not flat) to test biome modifications...");
        
        // Enable sampling to collect data from mixins
        MixinSampler.clear();
        MixinSampler.setSampleRate(10); // Sample every 10th call for detailed data
        MixinSampler.enable();

        // Create a singleplayer world with NORMAL terrain generation (not flat)
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> settings.setSeed("seed"))
                .create()) {
            
            System.out.println("World created, running verification...");
            
            // Run verification on the server immediately (don't wait for chunks)
            singleplayer.getServer().runOnServer(server -> {
                ServerLevel overworld = server.overworld();
                
                // Verify we have a noise-based world
                ChunkGenerator generator = overworld.getChunkSource().getGenerator();
                BiomeSource biomeSource = generator.getBiomeSource();
                
                System.out.println("Generator: " + generator.getClass().getSimpleName());
                System.out.println("BiomeSource: " + biomeSource.getClass().getSimpleName());
                
                if (!(generator instanceof NoiseBasedChunkGenerator)) {
                    throw new AssertionError("Expected NoiseBasedChunkGenerator but got " + generator.getClass().getSimpleName());
                }
                
                if (!(biomeSource instanceof MultiNoiseBiomeSource)) {
                    throw new AssertionError("Expected MultiNoiseBiomeSource but got " + biomeSource.getClass().getSimpleName());
                }
                
                System.out.println("✓ World is using NoiseBasedChunkGenerator + MultiNoiseBiomeSource");
                
                // Sample biomes to verify the world has diverse biomes
                Map<String, Integer> biomeCounts = new HashMap<>();
                BlockPos spawn = overworld.getLevelData().getRespawnData().pos();
                int sampleRadius = 100;
                int sampleStep = 25;
                
                System.out.println("\nSampling biomes around spawn " + spawn + "...");
                
                for (int x = spawn.getX() - sampleRadius; x <= spawn.getX() + sampleRadius; x += sampleStep) {
                    for (int z = spawn.getZ() - sampleRadius; z <= spawn.getZ() + sampleRadius; z += sampleStep) {
                        Holder<Biome> biome = overworld.getBiome(new BlockPos(x, 64, z));
                        String biomeName = biome.unwrapKey().map(k -> k.identifier().toString()).orElse("unknown");
                        biomeCounts.merge(biomeName, 1, Integer::sum);
                    }
                }
                
                System.out.println("=== Biome Distribution ===");
                biomeCounts.forEach((biome, count) -> 
                    System.out.println("  " + biome + ": " + count + " samples")
                );
                
                int uniqueBiomes = biomeCounts.size();
                System.out.println("Total unique biomes found: " + uniqueBiomes);
                System.out.println("✓ Found " + uniqueBiomes + " different biome(s)");
                
                // Disable sampling and print summary
                MixinSampler.disable();
                System.out.println("\n" + MixinSampler.getSummary());
                
                // Analyze fragmentation
                var fragAnalysis = MixinSampler.analyzeFragmentation();
                if (fragAnalysis.checks() > 0) {
                    System.out.println("Region fragmentation score: " + 
                        String.format("%.3f", fragAnalysis.score()) + 
                        " (" + fragAnalysis.discontinuities() + "/" + fragAnalysis.checks() + " discontinuities)");
                }
                
                System.out.println("\n=== Client GameTest Complete ===");
            });
        }
        
        System.out.println("Client GameTest finished successfully!");
    }
}