package com.terrasect.fabric.client;

import com.terrasect.common.api.Context;
import com.terrasect.common.api.Region;
import com.terrasect.common.compat.ResourceKeyCompat;
import com.terrasect.common.devtools.MixinSampler;
import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.generation.definition.ClimateSettings;
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
                Region regionAtPoint = World.getRegion(ctx, alignedBlockX, alignedBlockZ);
                String actualRegion = regionAtPoint != null ? regionAtPoint.name() : "NULL (no region!)";
                
                System.out.println("\nAt aligned coords (" + alignedBlockX + ", " + PROBLEM_COORD[1] + ", " + alignedBlockZ + "):");
                System.out.println("  World Biome: " + biomeName);
                System.out.println("  ACTUAL REGION (from World.getRegion): " + actualRegion);
                System.out.println("  Distance from origin: " + (int)Math.sqrt(
                    alignedBlockX*alignedBlockX + alignedBlockZ*alignedBlockZ));
                
                // Show hierarchy at this point
                System.out.println("\n  Region hierarchy at this point:");
                for (int depth = 1; depth <= 5; depth++) {
                    Region r = World.getRegionAtDepth(ctx, alignedBlockX, alignedBlockZ, depth);
                    System.out.println("    Depth " + depth + ": " + (r != null ? r.name() : "null"));
                }
                
                // ===== TERRAIN HEIGHT VERIFICATION =====
                System.out.println("\n=== TERRAIN HEIGHT VERIFICATION ===");
                // Check if the terrain height constraint is working
                int surfaceY = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, alignedBlockX, alignedBlockZ);
                int oceanFloorY = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, alignedBlockX, alignedBlockZ);
                int motionBlockY = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, alignedBlockX, alignedBlockZ);
                
                // Get the expected maxHeight from the region
                ClimateSettings climate = regionAtPoint != null ? regionAtPoint.definition().climate() : null;
                Integer expectedMaxHeight = climate != null ? climate.maxHeight() : null;
                
                System.out.println("At (" + alignedBlockX + ", " + alignedBlockZ + "):");
                System.out.println("  Region: " + actualRegion);
                System.out.println("  Expected maxHeight: " + (expectedMaxHeight != null ? expectedMaxHeight : "NONE (no constraint)"));
                System.out.println("  Actual surface height (WORLD_SURFACE): " + surfaceY);
                System.out.println("  Ocean floor height (OCEAN_FLOOR): " + oceanFloorY);
                System.out.println("  Motion blocking height (MOTION_BLOCKING): " + motionBlockY);
                
                if (expectedMaxHeight != null) {
                    if (oceanFloorY <= expectedMaxHeight) {
                        System.out.println("  ✓ TERRAIN CONSTRAINED CORRECTLY: ocean floor " + oceanFloorY + " <= maxHeight " + expectedMaxHeight);
                    } else {
                        System.out.println("  ✗ TERRAIN NOT CONSTRAINED: ocean floor " + oceanFloorY + " > maxHeight " + expectedMaxHeight);
                        System.out.println("    This indicates the height constraint is NOT working properly!");
                    }
                }
                
                // Sample additional points in BORDER region
                System.out.println("\n  Sampling additional BORDER region points for terrain heights:");
                int[] testX = {48, 90, 100, 150, 200};
                int[] testZ = {-333, -300, -250, -200, -150};
                for (int i = 0; i < testX.length; i++) {
                    int tx = testX[i];
                    int tz = testZ[i];
                    Region testRegion = World.getRegion(ctx, tx, tz);
                    if (testRegion != null && "BORDER".equals(testRegion.name())) {
                        int testFloorY = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, tx, tz);
                        int testSurfaceY = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, tx, tz);
                        String status = testFloorY <= 63 ? "✓" : "✗";
                        System.out.println("    " + status + " (" + tx + ", " + tz + "): floor=" + testFloorY + " surface=" + testSurfaceY);
                    }
                }
            });
            
            // Teleport to problem location, let player fall, then measure actual Y
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                // Teleport high above the problem coordinate
                player.teleportTo(PROBLEM_COORD[0], 200, PROBLEM_COORD[2]);
            });
            
            // Wait for chunks to generate and player to fall
            System.out.println("\n=== TELEPORTING TO PROBLEM LOCATION ===");
            System.out.println("Teleporting to (" + PROBLEM_COORD[0] + ", 200, " + PROBLEM_COORD[2] + ") and waiting for fall...");
            context.waitTicks(300);  // Wait longer for chunks to generate and player to fall
            
            // Now measure where the player actually is
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                double playerY = player.getY();
                int blockX = (int) Math.floor(player.getX());
                int blockY = (int) Math.floor(playerY);
                int blockZ = (int) Math.floor(player.getZ());
                
                System.out.println("\n=== ACTUAL TERRAIN HEIGHT (from player position) ===");
                System.out.println("Player landed at: (" + blockX + ", " + playerY + ", " + blockZ + ")");
                
                // Show what blocks are at and below the player - scan ALL the way down
                net.minecraft.server.level.ServerLevel level = server.overworld();
                System.out.println("Blocks at player column (from Y=100 down to Y=-64):");
                System.out.println("  Player Y position: " + playerY);
                System.out.println("  ---");
                for (int y = 100; y >= -64; y--) {
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(blockX, y, blockZ);
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                    String blockName = state.getBlock().getName().getString();
                    String marker = (y == blockY) ? " <-- player feet" : "";
                    // Only print non-air blocks to reduce noise, but show air around player
                    if (!state.isAir() || (y >= blockY - 3 && y <= blockY + 3)) {
                        System.out.println("  Y=" + y + ": " + blockName + marker);
                    }
                }
                
                // Get the region at this location
                Context ctx = MinecraftContext.get(net.minecraft.world.level.Level.OVERWORLD);
                Region region = ctx != null ? World.getRegion(ctx, blockX, blockZ) : null;
                String regionName = region != null ? region.name() : "NULL";
                
                ClimateSettings climate = region != null ? region.definition().climate() : null;
                Integer maxHeight = climate != null ? climate.maxHeight() : null;
                
                System.out.println("\nRegion: " + regionName);
                System.out.println("Expected maxHeight: " + (maxHeight != null ? maxHeight : "NONE"));
                System.out.println("Actual terrain height (player Y): " + (int) playerY);
                
                // Find the highest solid block
                int highestSolid = -64;
                for (int y = 319; y >= -64; y--) {
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(blockX, y, blockZ);
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && !state.getFluidState().isSource()) {
                        highestSolid = y;
                        System.out.println("Highest solid block at Y=" + y + ": " + state.getBlock().getName().getString());
                        break;
                    }
                }
                
                // Check actual biome at this location
                net.minecraft.core.BlockPos biomePos = new net.minecraft.core.BlockPos(blockX, 63, blockZ);
                var biomeHolder = level.getBiome(biomePos);
                String biomeName = biomeHolder.getRegisteredName();
                System.out.println("Biome at location: " + biomeName);
                
                if (maxHeight != null) {
                    // Check if highest solid is from terrain or structure
                    boolean isStructureBlock = false;
                    if (highestSolid > maxHeight) {
                        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(blockX, highestSolid, blockZ);
                        net.minecraft.world.level.block.state.BlockState highBlock = level.getBlockState(pos);
                        String blockName = highBlock.getBlock().getName().getString().toLowerCase();
                        // Structure blocks are typically brick, mossy, etc.
                        isStructureBlock = blockName.contains("brick") || blockName.contains("mossy") || 
                                          blockName.contains("suspicious") || blockName.contains("polished");
                    }
                    
                    if (highestSolid <= maxHeight) {
                        System.out.println("✓ TERRAIN CONSTRAINED: highest solid " + highestSolid + " <= maxHeight " + maxHeight);
                    } else if (isStructureBlock) {
                        System.out.println("✓ TERRAIN CONSTRAINED (structure above): base terrain at " + maxHeight + ", structure at " + highestSolid);
                    } else {
                        System.out.println("✗ TERRAIN NOT CONSTRAINED: highest solid " + highestSolid + " > maxHeight " + maxHeight);
                    }
                }
            });
            
            context.waitTicks(20);
            context.takeScreenshot("00_PROBLEM_LOCATION_x94_z-304");
            System.out.println("  Screenshot taken at problem location");
            
            System.out.println("\n=== Test Complete ===");
        }
        
        MixinSampler.disable();
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
