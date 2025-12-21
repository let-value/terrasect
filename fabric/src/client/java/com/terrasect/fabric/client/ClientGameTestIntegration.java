package com.terrasect.fabric.client;

import com.terrasect.common.api.Context;
import com.terrasect.common.api.Region;
import com.terrasect.common.compat.ResourceKeyCompat;
import com.terrasect.common.devtools.MixinSampler;
import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.runtime.World;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

import java.util.*;

/**
 * Client GameTest that creates a real world with noise-based terrain generation
 * to verify that our mod is properly modifying biome selection.
 * 
 * <p>Key test: Sample biomes at QUART-ALIGNED coordinates (multiples of 4 blocks)
 * because Minecraft stores biomes at 1/4 resolution. This ensures we compare
 * at the exact coordinates where biomes are actually sampled.
 * 
 * <p>Coordinate systems:
 * <ul>
 *   <li>Block coords: x, y, z (what players see)</li>
 *   <li>Quart coords: quartX = blockX >> 2 (biome storage resolution)</li>
 *   <li>To align block to quart: alignedBlock = (block >> 2) << 2</li>
 * </ul>
 */
public class ClientGameTestIntegration implements FabricClientGameTest {

    // SEASONS_HUB layout - SEASON_SIZE is 64 blocks (4 chunks) = 16 quarts
    private static final int SEASON_SIZE = 64;
    private static final int SAMPLE_RADIUS = 400;  // Go beyond hub to check borders
    private static final int SCREENSHOT_HEIGHT = 150;
    
    // Specific test coordinates from user observation (block coords)
    // Note: These will be aligned to quart boundaries for comparison
    private static final int[] PROBLEM_COORD = {94, 79, -304};  // Plains where there shouldn't be?
    
    // Quart-aligned version: 94 >> 2 = 23, 23 << 2 = 92
    // So the actual biome sample for block 94 happens at quart 23, which covers blocks 92-95

    @Override
    public void runTest(ClientGameTestContext context) {
        System.out.println("=== Client GameTest Starting ===");
        System.out.println("Creating a NORMAL world to verify MixinSampler matches reality...");
        
        // Enable sampling with high frequency to catch more samples
        MixinSampler.clear();
        MixinSampler.setSampleRate(1);  // Record EVERY call
        MixinSampler.enable();

        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed("seed");
                    settings.setGameMode(net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode.CREATIVE);
                })
                .create()) {
            
            System.out.println("World created, waiting for chunks to render...");
            singleplayer.getClientWorld().waitForChunksRender();
            
            // Take initial screenshot
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(5);
            context.takeScreenshot("01_spawn_overview");
            
            // ===== FIRST: Check specific problem coordinate =====
            System.out.println("\n=== Checking Specific Problem Coordinate ===");
            System.out.println("User reported: x=94, y=79, z=-304 shows plains biome with ocean ruins");
            System.out.println("\n=== COORDINATE ALIGNMENT EXPLANATION ===");
            System.out.println("Biomes are stored at QUART resolution (4 blocks per biome):");
            System.out.println("  Block 94 -> Quart 23 (94 >> 2) -> Aligned blocks 92-95 share same biome");
            System.out.println("  Block -304 -> Quart -76 (-304 >> 2) -> Aligned blocks -304 to -301 share same biome");
            
            singleplayer.getServer().runOnServer(server -> {
                ServerLevel overworld = server.overworld();
                
                // Get the MinecraftContext that was created when the world loaded
                Context ctx = MinecraftContext.get(net.minecraft.world.level.Level.OVERWORLD);
                if (ctx == null) {
                    System.out.println("ERROR: MinecraftContext not found for OVERWORLD!");
                    return;
                }
                
                // Show quart alignment for problem coordinate
                int quartX = PROBLEM_COORD[0] >> 2;  // 94 >> 2 = 23
                int quartZ = PROBLEM_COORD[2] >> 2;  // -304 >> 2 = -76
                int alignedBlockX = quartX << 2;     // 23 << 2 = 92
                int alignedBlockZ = quartZ << 2;     // -76 << 2 = -304
                
                System.out.println("\n  Problem coord: (" + PROBLEM_COORD[0] + ", " + PROBLEM_COORD[2] + ")");
                System.out.println("  Quart coords: (" + quartX + ", " + quartZ + ")");
                System.out.println("  Aligned block coords: (" + alignedBlockX + ", " + alignedBlockZ + ")");
                
                // Sample biome at the aligned block position
                BlockPos problemPos = new BlockPos(alignedBlockX, PROBLEM_COORD[1], alignedBlockZ);
                Holder<Biome> biome = overworld.getBiome(problemPos);
                String biomeName = getBiomeName(biome);
                
                // Query the ACTUAL region system using aligned coords (same as BiomeHandler)
                Region regionAtPoint = World.getRegion(World.OVERWORLD, alignedBlockX, alignedBlockZ, ctx);
                String actualRegion = regionAtPoint != null ? regionAtPoint.name() : "NULL (no region!)";
                
                System.out.println("\nAt aligned coords (" + alignedBlockX + ", " + PROBLEM_COORD[1] + ", " + alignedBlockZ + "):");
                System.out.println("  World Biome: " + biomeName);
                System.out.println("  ACTUAL REGION (from World.getRegion): " + actualRegion);
                System.out.println("  Distance from origin: " + (int)Math.sqrt(
                    alignedBlockX*alignedBlockX + alignedBlockZ*alignedBlockZ));
                
                // Show hierarchy at this point
                System.out.println("\n  Region hierarchy at this point:");
                for (int depth = 1; depth <= 5; depth++) {
                    Region r = World.getRegionAtDepth(World.OVERWORLD, alignedBlockX, alignedBlockZ, ctx, depth);
                    System.out.println("    Depth " + depth + ": " + (r != null ? r.name() : "null"));
                }
            });
            
            // Teleport to problem location and screenshot
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(PROBLEM_COORD[0], PROBLEM_COORD[1] + 50, PROBLEM_COORD[2]);
                player.setXRot(90f);
            });
            context.waitTicks(60);
            context.takeScreenshot("00_PROBLEM_LOCATION_x94_z-304");
            System.out.println("  Screenshot taken at problem location");
            
            // ===== Take corners at extended range =====
            System.out.println("\n=== Taking Screenshots at Extended Range ===");
            
            int[][] corners = {
                {0, 0, SCREENSHOT_HEIGHT},
                { SAMPLE_RADIUS,  SAMPLE_RADIUS, SCREENSHOT_HEIGHT},
                {-SAMPLE_RADIUS,  SAMPLE_RADIUS, SCREENSHOT_HEIGHT},
                { SAMPLE_RADIUS, -SAMPLE_RADIUS, SCREENSHOT_HEIGHT},
                {-SAMPLE_RADIUS, -SAMPLE_RADIUS, SCREENSHOT_HEIGHT},
                {94, -304, SCREENSHOT_HEIGHT},  // Problem coordinate
            };
            String[] cornerNames = {"CENTER", "NE", "NW", "SE", "SW", "PROBLEM"};
            
            for (int i = 0; i < corners.length; i++) {
                int x = corners[i][0];
                int z = corners[i][1];
                int y = corners[i][2];
                String name = cornerNames[i];
                
                final int fx = x, fy = y, fz = z;
                singleplayer.getServer().runOnServer(server -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                    player.teleportTo(fx, fy, fz);
                    player.setXRot(90f);
                    player.setYRot(0f);
                });
                
                context.waitTicks(60);
                context.takeScreenshot(String.format("02_corner_%s_x%d_z%d", name, x, z));
                System.out.println("  Screenshot taken at " + name + " (" + x + ", " + z + ")");
            }
            
            // ===== SPIRAL SAMPLING: Compare World vs MixinSampler =====
            System.out.println("\n=== Spiral Sampling at QUART-ALIGNED Coordinates ===");
            System.out.println("(All coordinates are multiples of 4 to match biome resolution)\n");
            
            singleplayer.getServer().runOnServer(server -> {
                ServerLevel overworld = server.overworld();
                
                // Verify generator type
                ChunkGenerator generator = overworld.getChunkSource().getGenerator();
                BiomeSource biomeSource = generator.getBiomeSource();
                System.out.println("Generator: " + generator.getClass().getSimpleName());
                System.out.println("BiomeSource: " + biomeSource.getClass().getSimpleName());
                
                if (!(generator instanceof NoiseBasedChunkGenerator)) {
                    throw new AssertionError("Expected NoiseBasedChunkGenerator");
                }
                if (!(biomeSource instanceof MultiNoiseBiomeSource)) {
                    throw new AssertionError("Expected MultiNoiseBiomeSource");
                }
                
                System.out.println("✓ Using NoiseBasedChunkGenerator + MultiNoiseBiomeSource\n");
                
                // Get the MinecraftContext that was created when the world loaded
                Context ctx = MinecraftContext.get(net.minecraft.world.level.Level.OVERWORLD);
                if (ctx == null) {
                    throw new AssertionError("MinecraftContext not found for OVERWORLD!");
                }
                System.out.println("World seed: " + overworld.getSeed());
                
                // Generate spiral sampling points at QUART boundaries (step = 4 blocks)
                // This ensures we hit every quart cell exactly once
                List<int[]> spiralPoints = generateSpiralPoints(SAMPLE_RADIUS, 4);  // Step of 4 for quart alignment
                
                // Also add specific problem coordinates (quart-aligned)
                spiralPoints.add(new int[]{(94 >> 2) << 2, (-304 >> 2) << 2});   // User's problem point (92, -304)
                spiralPoints.add(new int[]{0, (-304 >> 2) << 2});                  // Same Z, center X (0, -304)
                spiralPoints.add(new int[]{(-94 >> 2) << 2, (-304 >> 2) << 2});  // Mirror (=-96, -304)
                
                System.out.println("Sampling " + spiralPoints.size() + " quart-aligned points...\n");
                
                // Map biomes to expected regions (for comparison only)
                Map<String, String> biomeToRegion = createBiomeToRegionMap();
                
                // Track results - using ACTUAL region from World.getRegion, not inferred
                Map<String, Integer> worldBiomeCounts = new LinkedHashMap<>();
                Map<String, Integer> actualRegionCounts = new LinkedHashMap<>();
                Map<String, Integer> inferredRegionCounts = new LinkedHashMap<>();
                
                // Track mismatches between actual region and biome-inferred region
                List<String> mismatches = new ArrayList<>();
                
                // Track coordinate comparison with MixinSampler
                var mixinBiomeSamples = MixinSampler.getBiomeSamples();
                Map<String, String> mixinCoordToBiome = new HashMap<>();  // "quartX,quartZ" -> biome
                Map<String, String> mixinCoordToRegion = new HashMap<>(); // "quartX,quartZ" -> region
                for (var sample : mixinBiomeSamples) {
                    String key = sample.quartX() + "," + sample.quartZ();
                    mixinCoordToBiome.put(key, sample.selectedBiome());
                    mixinCoordToRegion.put(key, sample.regionName());
                }
                System.out.println("MixinSampler has " + mixinBiomeSamples.size() + " biome samples for comparison");
                
                System.out.println("\n╔══════╦════════════╦═══════════╦════════════════════════╦═════════════╦═════════════╦═════════════════════════╗");
                System.out.println("║ Dist ║Block Coords║Quart Coord║        Biome           ║ ACTUAL Reg  ║ Mixin Reg   ║ Match?                  ║");
                System.out.println("╠══════╬════════════╬═══════════╬════════════════════════╬═════════════╬═════════════╬═════════════════════════╣");
                
                int printed = 0;
                int coordMatches = 0;
                int coordMismatches = 0;
                for (int[] point : spiralPoints) {
                    int blockX = point[0];
                    int blockZ = point[1];
                    int quartX = blockX >> 2;
                    int quartZ = blockZ >> 2;
                    int dist = (int) Math.sqrt(blockX*blockX + blockZ*blockZ);
                    
                    // Get biome from world (this is what our filtering actually produced)
                    BlockPos pos = new BlockPos(blockX, 64, blockZ);
                    Holder<Biome> biome = overworld.getBiome(pos);
                    String biomeName = getBiomeName(biome);
                    
                    // Get ACTUAL region from World API (using aligned block coords)
                    Region actualRegion = World.getRegion(World.OVERWORLD, blockX, blockZ, ctx);
                    String actualRegionName = actualRegion != null ? actualRegion.name() : "NULL";
                    
                    // Get what MixinSampler recorded at these quart coords
                    String quartKey = quartX + "," + quartZ;
                    String mixinBiome = mixinCoordToBiome.get(quartKey);
                    String mixinRegion = mixinCoordToRegion.get(quartKey);
                    
                    // Get inferred region from biome name (for comparison)
                    String inferredRegion = biomeToRegion.getOrDefault(biomeName, "OTHER");
                    
                    worldBiomeCounts.merge(biomeName, 1, Integer::sum);
                    actualRegionCounts.merge(actualRegionName, 1, Integer::sum);
                    inferredRegionCounts.merge(inferredRegion, 1, Integer::sum);
                    
                    // Check for mismatch between world and mixin at SAME COORDINATES
                    String matchStatus;
                    if (mixinRegion == null) {
                        matchStatus = "no mixin sample";
                    } else if (actualRegionName.equals(mixinRegion)) {
                        matchStatus = "✓ match";
                        coordMatches++;
                    } else {
                        matchStatus = "✗ MISMATCH";
                        coordMismatches++;
                        if (mismatches.size() < 20) {
                            mismatches.add(String.format("  Quart(%d,%d) Block(%d,%d): World=%s, Mixin=%s, Biome=%s",
                                quartX, quartZ, blockX, blockZ, actualRegionName, mixinRegion, 
                                biomeName.replace("minecraft:", "")));
                        }
                    }
                    
                    // Print selected samples
                    if (printed < 20 || printed % 25 == 0 || matchStatus.contains("MISMATCH")) {
                        String shortBiome = biomeName.replace("minecraft:", "");
                        if (shortBiome.length() > 22) shortBiome = shortBiome.substring(0, 22);
                        String mixinRegStr = mixinRegion != null ? mixinRegion : "-";
                        System.out.printf("║ %4d ║ %4d,%5d ║ %4d,%4d ║ %-22s ║ %-11s ║ %-11s ║ %-23s ║%n",
                            dist, blockX, blockZ, quartX, quartZ, shortBiome, actualRegionName, mixinRegStr, matchStatus);
                    }
                    printed++;
                }
                
                System.out.println("╚══════╩════════════╩═══════════╩════════════════════════╩═════════════╩═════════════╩═════════════════════════╝");
                System.out.println("\nCoordinate-matched comparison: " + coordMatches + " matches, " + coordMismatches + " mismatches");
                
                int totalSamples = spiralPoints.size();
                
                // CRITICAL: Show what coordinates MixinSampler actually recorded
                System.out.println("\n=== MixinSampler Recorded Coordinates (first 30, sorted by distance from origin) ===");
                mixinBiomeSamples.stream()
                    .sorted((a, b) -> {
                        int distA = a.blockX() * a.blockX() + a.blockZ() * a.blockZ();
                        int distB = b.blockX() * b.blockX() + b.blockZ() * b.blockZ();
                        return Integer.compare(distA, distB);
                    })
                    .limit(30)
                    .forEach(sample -> {
                        int dist = (int) Math.sqrt(sample.blockX() * sample.blockX() + sample.blockZ() * sample.blockZ());
                        System.out.printf("  Dist %4d: Quart(%4d,%4d) Block(%5d,%5d) -> %s in region %s%n",
                            dist, sample.quartX(), sample.quartZ(), sample.blockX(), sample.blockZ(),
                            sample.selectedBiome().replace("minecraft:", ""), sample.regionName());
                    });
                
                // Check: are MixinSampler samples near our test area at all?
                long samplesNearSpawn = mixinBiomeSamples.stream()
                    .filter(s -> Math.abs(s.blockX()) <= SAMPLE_RADIUS && Math.abs(s.blockZ()) <= SAMPLE_RADIUS)
                    .count();
                System.out.println("\nMixinSampler samples within " + SAMPLE_RADIUS + " blocks of origin: " + samplesNearSpawn + " / " + mixinBiomeSamples.size());
                
                // NOW: Sample the world at MixinSampler's recorded coordinates
                // This lets us compare apples-to-apples: what the mixin recorded vs what the world shows
                System.out.println("\n=== DIRECT COMPARISON: MixinSampler coords vs World at same coords ===");
                System.out.println("Sampling world biome at locations where MixinSampler recorded samples...\n");
                
                int mixinVsWorldMatches = 0;
                int mixinVsWorldMismatches = 0;
                List<String> discrepancies = new ArrayList<>();
                
                // Take first 200 MixinSampler samples and check them
                var samplesToCheck = mixinBiomeSamples.stream().limit(200).toList();
                for (var sample : samplesToCheck) {
                    // Get what MixinSampler recorded
                    String mixinBiome = sample.selectedBiome();
                    String mixinRegion = sample.regionName();
                    int blockX = sample.blockX();
                    int blockZ = sample.blockZ();
                    
                    // Query the world at these exact coordinates
                    BlockPos pos = new BlockPos(blockX, 64, blockZ);
                    Holder<Biome> worldBiome = overworld.getBiome(pos);
                    String worldBiomeName = getBiomeName(worldBiome);
                    
                    // Query our region system at these coordinates  
                    Region worldRegion = World.getRegion(World.OVERWORLD, blockX, blockZ, ctx);
                    String worldRegionName = worldRegion != null ? worldRegion.name() : "NULL";
                    
                    // Compare
                    boolean biomeMatch = mixinBiome.equals(worldBiomeName);
                    boolean regionMatch = mixinRegion.equals(worldRegionName) || 
                                         (mixinRegion == null && worldRegionName.equals("NULL"));
                    
                    if (biomeMatch && regionMatch) {
                        mixinVsWorldMatches++;
                    } else {
                        mixinVsWorldMismatches++;
                        if (discrepancies.size() < 20) {
                            discrepancies.add(String.format(
                                "  Block(%5d,%5d): Mixin[%s/%s] vs World[%s/%s]%s%s",
                                blockX, blockZ,
                                mixinBiome.replace("minecraft:", ""), mixinRegion,
                                worldBiomeName.replace("minecraft:", ""), worldRegionName,
                                biomeMatch ? "" : " BIOME_DIFF",
                                regionMatch ? "" : " REGION_DIFF"));
                        }
                    }
                }
                
                System.out.println("Compared " + samplesToCheck.size() + " MixinSampler samples to World:");
                System.out.println("  MATCHES: " + mixinVsWorldMatches + " (mixin recording matches world query)");
                System.out.println("  MISMATCHES: " + mixinVsWorldMismatches);
                
                if (!discrepancies.isEmpty()) {
                    System.out.println("\nDiscrepancies (first 20):");
                    for (String d : discrepancies) {
                        System.out.println(d);
                    }
                }
                
                // Print coordinate mismatches (World vs MixinSampler at same quart coords)
                if (!mismatches.isEmpty()) {
                    System.out.println("\n=== COORDINATE MISMATCHES: World vs MixinSampler at same quart ===");
                    System.out.println("These show where the region lookup returned different values:");
                    for (String m : mismatches) {
                        System.out.println(m);
                    }
                    System.out.println("\nPossible causes:");
                    System.out.println("  1. Different seed/context being used");
                    System.out.println("  2. Coordinate transformation bug");
                    System.out.println("  3. Cache timing issue between generation and query");
                }
                
                // Print biome distribution
                System.out.println("\n=== World Biome Distribution ===");
                worldBiomeCounts.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .forEach(e -> {
                        float pct = 100f * e.getValue() / totalSamples;
                        String region = biomeToRegion.getOrDefault(e.getKey(), "OTHER");
                        System.out.printf("  %-35s %4d (%5.1f%%) -> inferred: %s%n", 
                            e.getKey(), e.getValue(), pct, region);
                    });
                
                // Print ACTUAL region distribution (from World.getRegion)
                System.out.println("\n=== ACTUAL Region Distribution (from World.getRegion API) ===");
                actualRegionCounts.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .forEach(e -> {
                        float pct = 100f * e.getValue() / totalSamples;
                        System.out.printf("  %-15s %4d samples (%5.1f%%)%n", e.getKey(), e.getValue(), pct);
                    });
                
                // Print inferred region distribution (for comparison)
                System.out.println("\n=== Inferred Region Distribution (from biome names) ===");
                inferredRegionCounts.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .forEach(e -> {
                        float pct = 100f * e.getValue() / totalSamples;
                        System.out.printf("  %-15s %4d samples (%5.1f%%)%n", e.getKey(), e.getValue(), pct);
                    });
                
                // Now compare with MixinSampler data
                MixinSampler.disable();
                
                System.out.println("\n" + "=".repeat(60));
                System.out.println("=== MixinSampler Internal Data ===");
                System.out.println("=".repeat(60));
                
                Map<String, Integer> mixinRegionCounts = MixinSampler.getRegionCounts();
                long totalRegionQueries = MixinSampler.getRegionCallCount();
                
                System.out.println("\nMixinSampler recorded " + totalRegionQueries + " region queries");
                System.out.println("\n--- Region Distribution (from MixinSampler) ---");
                mixinRegionCounts.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .forEach(e -> {
                        float pct = 100f * e.getValue() / totalRegionQueries;
                        System.out.printf("  %-20s %8d (%5.1f%%)%n", e.getKey(), e.getValue(), pct);
                    });
                
                // Compare the two distributions
                System.out.println("\n" + "=".repeat(60));
                System.out.println("=== COMPARISON: ACTUAL Regions vs MixinSampler ===");
                System.out.println("=".repeat(60));
                
                System.out.println("\n╔═══════════════╦═══════════════╦═══════════════╦═══════════╗");
                System.out.println("║    Region     ║ ACTUAL World  ║ MixinSampler  ║   Match?  ║");
                System.out.println("╠═══════════════╬═══════════════╬═══════════════╬═══════════╣");
                
                Set<String> allRegions = new TreeSet<>();
                allRegions.addAll(actualRegionCounts.keySet());
                allRegions.addAll(mixinRegionCounts.keySet());
                
                for (String region : allRegions) {
                    int worldCount = actualRegionCounts.getOrDefault(region, 0);
                    float worldPct = 100f * worldCount / totalSamples;
                    
                    int mixinCount = mixinRegionCounts.getOrDefault(region, 0);
                    float mixinPct = totalRegionQueries > 0 ? 100f * mixinCount / totalRegionQueries : 0;
                    
                    // Check if they roughly match (within 10% absolute)
                    boolean match = Math.abs(worldPct - mixinPct) < 10 || 
                                   (worldPct > 0 && mixinPct > 0);  // Both present is OK
                    String matchStr = match ? "✓" : "✗ MISMATCH";
                    
                    System.out.printf("║ %-13s ║ %6.1f%%       ║ %6.1f%%       ║ %-9s ║%n",
                        region, worldPct, mixinPct, matchStr);
                }
                
                System.out.println("╚═══════════════╩═══════════════╩═══════════════╩═══════════╝");
                
                // Print full MixinSampler summary
                System.out.println("\n" + MixinSampler.getSummary());
                
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
        System.out.println("Screenshots saved to: fabric/build/run/clientGameTest/screenshots/");
    }
    
    /**
     * Generate points in a spiral pattern from origin outward.
     */
    private List<int[]> generateSpiralPoints(int maxRadius, int step) {
        List<int[]> points = new ArrayList<>();
        
        // Start at center
        points.add(new int[]{0, 0});
        
        // Spiral outward
        int x = 0, z = 0;
        int dx = step, dz = 0;
        int segmentLength = 1;
        int segmentPassed = 0;
        int turns = 0;
        
        while (Math.abs(x) <= maxRadius && Math.abs(z) <= maxRadius) {
            // Move in current direction
            x += dx;
            z += dz;
            segmentPassed++;
            
            if (Math.abs(x) <= maxRadius && Math.abs(z) <= maxRadius) {
                points.add(new int[]{x, z});
            }
            
            // Check if we need to turn
            if (segmentPassed == segmentLength) {
                segmentPassed = 0;
                
                // Turn 90 degrees counter-clockwise
                int temp = dx;
                dx = -dz;
                dz = temp;
                turns++;
                
                // After every 2 turns, increase segment length
                if (turns % 2 == 0) {
                    segmentLength++;
                }
            }
            
            // Safety limit
            if (points.size() > 10000) break;
        }
        
        return points;
    }
    
    /**
     * Create mapping from biome names to expected regions.
     * NOTE: Some biomes appear in multiple regions (e.g., plains is in both SPAWN and SPRING)
     * so this mapping shows the MOST LIKELY region, but isn't definitive.
     */
    private Map<String, String> createBiomeToRegionMap() {
        Map<String, String> map = new HashMap<>();
        // SPAWN (plains only - now unique!)
        map.put("minecraft:plains", "SPAWN");
        // SPRING (unique biomes - no plains)
        map.put("minecraft:flower_forest", "SPRING");
        map.put("minecraft:meadow", "SPRING");
        map.put("minecraft:sunflower_plains", "SPRING");
        map.put("minecraft:cherry_grove", "SPRING");
        // SUMMER
        map.put("minecraft:desert", "SUMMER");
        map.put("minecraft:savanna", "SUMMER");
        map.put("minecraft:savanna_plateau", "SUMMER");
        map.put("minecraft:badlands", "SUMMER");
        map.put("minecraft:eroded_badlands", "SUMMER");
        // AUTUMN
        map.put("minecraft:forest", "AUTUMN");
        map.put("minecraft:dark_forest", "AUTUMN");
        map.put("minecraft:birch_forest", "AUTUMN");
        map.put("minecraft:old_growth_birch_forest", "AUTUMN");
        // WINTER
        map.put("minecraft:snowy_plains", "WINTER");
        map.put("minecraft:snowy_taiga", "WINTER");
        map.put("minecraft:ice_spikes", "WINTER");
        map.put("minecraft:frozen_river", "WINTER");
        map.put("minecraft:snowy_beach", "WINTER");
        // BORDER (deep ocean ring)
        map.put("minecraft:deep_ocean", "BORDER");
        map.put("minecraft:deep_cold_ocean", "BORDER");
        map.put("minecraft:deep_frozen_ocean", "BORDER");
        map.put("minecraft:deep_lukewarm_ocean", "BORDER");
        return map;
    }
    
    /**
     * Get biome name using version-compatible ResourceKeyCompat.
     */
    private String getBiomeName(Holder<Biome> biome) {
        return biome.unwrapKey()
            .map(ResourceKeyCompat::getKeyId)
            .orElse("unknown");
    }
}
