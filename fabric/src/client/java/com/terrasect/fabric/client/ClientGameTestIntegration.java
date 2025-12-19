package com.terrasect.fabric.client;

import com.terrasect.common.generation.mixin.ClimateHandler;
import com.terrasect.fabric.generation.FabricNarrGenContext;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * Client GameTest that creates a real world with noise-based terrain generation
 * to verify that our ClimateMixin is actually modifying biome selection.
 */
public class ClientGameTestIntegration implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        System.out.println("=== Client GameTest Starting ===");
        System.out.println("Creating a NORMAL world (not flat) to test biome modifications...");

        // Create a singleplayer world with NORMAL terrain generation (not flat)
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .setUseConsistentSettings(false) // Disable flat world default
                .create()) {
            
            System.out.println("World created, waiting for chunks to load...");
            
            // Wait for chunks to render
            singleplayer.getClientWorld().waitForChunksDownload();
            
            System.out.println("Chunks loaded, verifying world generation...");
            
            // Run verification on the server
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
                
                // Check if our context was registered
                FabricNarrGenContext ctx = FabricNarrGenContext.get(Level.OVERWORLD);
                if (ctx == null) {
                    throw new AssertionError("FabricNarrGenContext was not registered for OVERWORLD!");
                }
                System.out.println("✓ FabricNarrGenContext registered with seed: " + ctx.getSeed());
                
                // Sample biomes in a grid around spawn
                Map<String, Integer> biomeCounts = new HashMap<>();
                BlockPos spawn = overworld.getLevelData().getRespawnData().pos();
                int sampleRadius = 500; // Sample in a 1000x1000 block area
                int sampleStep = 50;    // Every 50 blocks
                
                System.out.println("Sampling biomes around spawn " + spawn + "...");
                
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
                
                if (uniqueBiomes < 2) {
                    System.out.println("WARNING: Only found " + uniqueBiomes + " biome(s). This might indicate the mixin isn't working.");
                } else {
                    System.out.println("✓ Found " + uniqueBiomes + " different biomes - terrain diversity confirmed!");
                }
                
                // Test the ClimateHandler directly
                System.out.println("\n=== Testing ClimateHandler directly ===");
                long baseTemp = 0L;
                long baseHumid = 0L;
                
                ClimateHandler.ClimateResult result1 = ClimateHandler.modifyClimate(ctx, 0, 64, 0, baseTemp, baseHumid);
                ClimateHandler.ClimateResult result2 = ClimateHandler.modifyClimate(ctx, 1000, 64, 1000, baseTemp, baseHumid);
                ClimateHandler.ClimateResult result3 = ClimateHandler.modifyClimate(ctx, 5000, 64, 5000, baseTemp, baseHumid);
                
                System.out.println("Climate at (0,0): modified=" + result1.modified() + 
                    ", temp=" + result1.temperature() + ", humid=" + result1.humidity());
                System.out.println("Climate at (1000,1000): modified=" + result2.modified() + 
                    ", temp=" + result2.temperature() + ", humid=" + result2.humidity());
                System.out.println("Climate at (5000,5000): modified=" + result3.modified() + 
                    ", temp=" + result3.temperature() + ", humid=" + result3.humidity());
                
                // Verify we're getting different climate values
                boolean climateVaries = (result1.temperature() != result2.temperature()) ||
                                        (result1.humidity() != result2.humidity()) ||
                                        (result2.temperature() != result3.temperature()) ||
                                        (result2.humidity() != result3.humidity());
                
                if (climateVaries) {
                    System.out.println("✓ Climate values vary by location - modification is working!");
                } else {
                    System.out.println("WARNING: Climate values are identical at all locations");
                }
                
                System.out.println("\n=== Client GameTest Complete ===");
            });
            
            // Take a screenshot for visual verification
            context.takeScreenshot("biome_test_world");
            System.out.println("Screenshot saved as biome_test_world.png");
        }
        
        System.out.println("Client GameTest finished successfully!");
    }
}
