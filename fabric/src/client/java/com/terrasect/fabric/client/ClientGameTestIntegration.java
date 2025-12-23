package com.terrasect.fabric.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client game test to verify terrain modification (height constraints) and ocean generation.
 * Teleports to specific coordinates and inspects the block column.
 */
public class ClientGameTestIntegration implements FabricClientGameTest {
    
    // Specific test coordinates from user observation (block coords)
    // Note: These will be aligned to quart boundaries for comparison
    private static final int[] PROBLEM_COORD = {94, 79, -304};  // Plains where there shouldn't be?
    
    // Quart-aligned version: 94 >> 2 = 23, 23 << 2 = 92
    // So the actual biome sample for block 94 happens at quart 23, which covers blocks 92-95
    
    @Override
    public void runTest(ClientGameTestContext context) {
        System.out.println("=== Terrain Modification Test Starting ===");
        System.out.println("Testing terrain height constraints and ocean generation...");
        System.out.println("Target coordinate: (" + PROBLEM_COORD[0] + ", " + PROBLEM_COORD[1] + ", " + PROBLEM_COORD[2] + ")");

        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed("seed");
                    settings.setGameMode(
                            net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode.CREATIVE);
                })
                .create()) {

            System.out.println("World created, waiting for chunks to render...");
            singleplayer.getClientWorld().waitForChunksRender();
            
            // Teleport high above the problem coordinate
            System.out.println("\n=== Teleporting to test location ===");
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                // Teleport high above the problem coordinate
                player.teleportTo(PROBLEM_COORD[0], 200, PROBLEM_COORD[2]);
            });
            
            // Wait for chunks to load around new position
            context.waitTicks(60);
            singleplayer.getClientWorld().waitForChunksRender();
            
            // Take screenshot from above
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(5);
            context.takeScreenshot("01_above_test_location");
            
            // Now analyze the terrain
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                double playerY = player.getY();
                int blockX = (int) Math.floor(player.getX());
                int blockY = (int) Math.floor(playerY);
                int blockZ = (int) Math.floor(player.getZ());
                
                System.out.println("\n=== TERRAIN ANALYSIS AT TEST LOCATION ===");
                System.out.println("Player position: (" + blockX + ", " + playerY + ", " + blockZ + ")");
                
                // Show what blocks are at and below the player - scan ALL the way down
                ServerLevel level = server.overworld();
                System.out.println("\nBlocks at player column (from Y=100 down to Y=-64):");
                System.out.println("  Target X,Z: (" + PROBLEM_COORD[0] + ", " + PROBLEM_COORD[2] + ")");
                System.out.println("  ---");
                
                int highestSolid = Integer.MIN_VALUE;
                int lowestAir = Integer.MAX_VALUE;
                
                for (int y = 100; y >= -64; y--) {
                    BlockPos pos = new BlockPos(PROBLEM_COORD[0], y, PROBLEM_COORD[2]);
                    BlockState state = level.getBlockState(pos);
                    String blockName = state.getBlock().getName().getString();
                    
                    // Track terrain stats
                    if (!state.isAir() && y > highestSolid) {
                        highestSolid = y;
                    }
                    if (state.isAir() && y < lowestAir && y > -60) {
                        lowestAir = y;
                    }
                    
                    // Only print non-air blocks to reduce noise, but show air around surface
                    if (!state.isAir() || (highestSolid != Integer.MIN_VALUE && y >= highestSolid - 3 && y <= highestSolid + 5)) {
                        System.out.println("  Y=" + y + ": " + blockName);
                    }
                }
                
                System.out.println("\n=== TERRAIN SUMMARY ===");
                System.out.println("  Highest solid block: Y=" + highestSolid);
                System.out.println("  Expected max height: 40-55 (configured range)");
                
                // Sample multiple columns to show height variation
                System.out.println("\n=== TERRAIN HEIGHT VARIATION (sampling 16x16 area) ===");
                int[] surfaceHeights = new int[256];
                int minSurface = Integer.MAX_VALUE;
                int maxSurface = Integer.MIN_VALUE;
                for (int dz = 0; dz < 16; dz++) {
                    for (int dx = 0; dx < 16; dx++) {
                        int x = PROBLEM_COORD[0] + dx;
                        int z = PROBLEM_COORD[2] + dz;
                        // Find surface (first solid non-structure block from top)
                        for (int y = 62; y >= 30; y--) {
                            BlockPos pos = new BlockPos(x, y, z);
                            BlockState state = level.getBlockState(pos);
                            if (!state.isAir() && !state.getFluidState().isSource()) {
                                surfaceHeights[dx + dz * 16] = y;
                                minSurface = Math.min(minSurface, y);
                                maxSurface = Math.max(maxSurface, y);
                                break;
                            }
                        }
                    }
                }
                System.out.println("  Surface height range: Y=" + minSurface + " to Y=" + maxSurface);
                System.out.println("  Expected: Y=40 to Y=55 (with natural variation)");
                
                // Check if there's water (ocean)
                boolean hasWater = false;
                int waterLevel = -1;
                for (int y = 100; y >= -64; y--) {
                    BlockPos pos = new BlockPos(PROBLEM_COORD[0], y, PROBLEM_COORD[2]);
                    BlockState state = level.getBlockState(pos);
                    if (state.getFluidState().isSource()) {
                        hasWater = true;
                        if (waterLevel < 0) waterLevel = y;
                    }
                }
                
                if (hasWater) {
                    System.out.println("  Water found at Y=" + waterLevel);
                } else {
                    System.out.println("  No water found in column");
                }
            });
            
            // Take final screenshot
            context.waitTicks(20);
            context.takeScreenshot("02_terrain_view");
            
            System.out.println("\n=== Terrain Modification Test Complete ===");
        }
    }
}
