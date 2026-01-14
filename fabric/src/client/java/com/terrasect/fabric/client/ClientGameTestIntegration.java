package com.terrasect.fabric.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;

public class ClientGameTestIntegration implements FabricClientGameTest {

    private static final int[] PROBLEM_COORD = {94, 79, -304};

    @Override
    public void runTest(ClientGameTestContext context) {
        System.out.println("=== Terrain Modification Test Starting ===");
        System.out.println("Testing terrain height constraints and ocean generation...");
        System.out.println(
                "Target coordinate: (" + PROBLEM_COORD[0] + ", " + PROBLEM_COORD[1] + ", " + PROBLEM_COORD[2] + ")");

        try (var singleplayer = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed("seed");
                    settings.setGameMode(
                            net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode
                                    .CREATIVE);
                })
                .create()) {

            System.out.println("World created, waiting for chunks to render...");
            singleplayer.getClientLevel().waitForChunksRender();

            System.out.println("\n=== Teleporting to test location ===");
            singleplayer.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().get(0);

                player.teleportTo(PROBLEM_COORD[0], 200, PROBLEM_COORD[2]);
            });

            context.waitTicks(60);
            singleplayer.getClientLevel().waitForChunksRender();

            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(5);
            context.takeScreenshot("01_above_test_location");

            singleplayer.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().get(0);
                var playerY = player.getY();
                var blockX = (int) Math.floor(player.getX());
                var blockZ = (int) Math.floor(player.getZ());

                System.out.println("\n=== TERRAIN ANALYSIS AT TEST LOCATION ===");
                System.out.println("Player position: (" + blockX + ", " + playerY + ", " + blockZ + ")");

                var level = server.overworld();
                System.out.println("\nBlocks at player column (from Y=100 down to Y=-64):");
                System.out.println("  Target X,Z: (" + PROBLEM_COORD[0] + ", " + PROBLEM_COORD[2] + ")");
                System.out.println("  ---");

                var highestSolid = Integer.MIN_VALUE;
                var lowestAir = Integer.MAX_VALUE;

                for (var y = 100; y >= -64; y--) {
                    var pos = new BlockPos(PROBLEM_COORD[0], y, PROBLEM_COORD[2]);
                    var state = level.getBlockState(pos);
                    var blockName = state.getBlock().getName().getString();

                    if (!state.isAir() && y > highestSolid) {
                        highestSolid = y;
                    }
                    if (state.isAir() && y < lowestAir && y > -60) {
                        lowestAir = y;
                    }

                    if (!state.isAir()
                            || (highestSolid != Integer.MIN_VALUE && y >= highestSolid - 3 && y <= highestSolid + 5)) {
                        System.out.println("  Y=" + y + ": " + blockName);
                    }
                }

                System.out.println("\n=== TERRAIN SUMMARY ===");
                System.out.println("  Highest solid block: Y=" + highestSolid);
                System.out.println("  Expected max height: 40-55 (configured range)");

                System.out.println("\n=== TERRAIN HEIGHT VARIATION (sampling 16x16 area) ===");
                int[] surfaceHeights = new int[256];
                var minSurface = Integer.MAX_VALUE;
                var maxSurface = Integer.MIN_VALUE;
                for (var dz = 0; dz < 16; dz++) {
                    for (var dx = 0; dx < 16; dx++) {
                        var x = PROBLEM_COORD[0] + dx;
                        var z = PROBLEM_COORD[2] + dz;

                        for (var y = 62; y >= 30; y--) {
                            var pos = new BlockPos(x, y, z);
                            var state = level.getBlockState(pos);
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

                var hasWater = false;
                var waterLevel = -1;
                for (var y = 100; y >= -64; y--) {
                    var pos = new BlockPos(PROBLEM_COORD[0], y, PROBLEM_COORD[2]);
                    var state = level.getBlockState(pos);
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

            context.waitTicks(20);
            context.takeScreenshot("02_terrain_view");

            System.out.println("\n=== Terrain Modification Test Complete ===");
        }
    }
}
