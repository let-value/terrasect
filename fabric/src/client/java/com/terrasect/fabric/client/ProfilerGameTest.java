package com.terrasect.fabric.client;

import com.terrasect.common.Terrasect;
import com.terrasect.common.devtools.Profiler;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/**
 * Client game test that profiles world generation performance.
 * 
 * <p>This test:
 * <ol>
 *   <li>Enables the profiler</li>
 *   <li>Creates a world with a static seed for reproducibility</li>
 *   <li>Teleports to multiple locations to trigger chunk generation</li>
 *   <li>Collects and reports profiler statistics</li>
 * </ol>
 * 
 * <p>Run with: {@code ./gradlew :fabric:runClientGameTest}
 */
public class ProfilerGameTest implements FabricClientGameTest {
    
    /** Static seed for reproducible profiling runs */
    private static final String WORLD_SEED = "profiler-test-seed-12345";
    
    /** Locations to teleport to (x, z coordinates in blocks) */
    private static final int[][] TELEPORT_LOCATIONS = {
        {0, 0},           // Spawn area
        {1000, 0},        // East
        {0, 1000},        // South  
        {-1000, 0},       // West
        {0, -1000},       // North
        {2000, 2000},     // Far diagonal
        {-2000, -2000},   // Opposite diagonal
        {5000, 0},        // Very far east
    };
    
    /** Ticks to wait at each location for chunks to generate */
    private static final int TICKS_PER_LOCATION = 60; // ~3 seconds at 20 TPS
    
    @Override
    public void runTest(ClientGameTestContext context) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    TERRASECT PROFILER GAME TEST");
        System.out.println("=".repeat(80));
        System.out.println("This test measures world generation performance across multiple locations.");
        System.out.println("Seed: " + WORLD_SEED);
        System.out.println("Locations to visit: " + TELEPORT_LOCATIONS.length);
        System.out.println("=".repeat(80) + "\n");
        
        // Enable profiler (this also resets metrics)
        Profiler.enable();
        Terrasect.LOGGER.info("[ProfilerGameTest] Profiler enabled");
        
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed(WORLD_SEED);
                    settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                })
                .create()) {
            
            System.out.println("[ProfilerGameTest] World created, beginning chunk generation profiling...\n");
            
            // Wait for initial spawn chunks
            singleplayer.getClientWorld().waitForChunksRender();
            context.waitTicks(TICKS_PER_LOCATION);
            
            int locationIndex = 0;
            for (int[] loc : TELEPORT_LOCATIONS) {
                locationIndex++;
                final int x = loc[0];
                final int z = loc[1];
                final int locNum = locationIndex;
                
                System.out.printf("[ProfilerGameTest] Location %d/%d: Teleporting to (%d, ~, %d)%n",
                    locNum, TELEPORT_LOCATIONS.length, x, z);
                
                // Teleport player to location (y=100 for safe height)
                context.runOnClient(client -> {
                    if (client.player != null) {
                        client.player.setPos(x, 100, z);
                    }
                });
                
                // Wait for chunks to generate and render
                context.waitTicks(10); // Brief pause before checking
                singleplayer.getClientWorld().waitForChunksRender();
                context.waitTicks(TICKS_PER_LOCATION);
                
                // Print intermediate stats every few locations
                if (locNum % 3 == 0) {
                    printIntermediateStats();
                }
            }
            
            System.out.println("\n[ProfilerGameTest] All locations visited. Collecting final statistics...\n");
            
            // Take a final screenshot
            context.takeScreenshot("profiler_test_final_location");
        }
        
        // Print final profiler summary
        printFinalSummary();
        
        // Disable profiler
        Profiler.disable();
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    PROFILER GAME TEST COMPLETE");
        System.out.println("=".repeat(80) + "\n");
    }
    
    private void printIntermediateStats() {
        System.out.println("\n--- Intermediate Stats ---");
        
        var climateMetric = Profiler.getMetric(Profiler.CLIMATE_MODIFY);
        var biomeMetric = Profiler.getMetric(Profiler.BIOME_SELECT);
        var regionFieldMetric = Profiler.getMetric(Profiler.REGION_FIELD_DATA);
        
        System.out.printf("  Climate modifications: %,d calls, %.2f ms total, %.2f µs avg%n",
            climateMetric.calls(), climateMetric.totalMs(), climateMetric.avgUs());
        System.out.printf("  Biome selections: %,d calls, %.2f ms total, %.2f µs avg%n",
            biomeMetric.calls(), biomeMetric.totalMs(), biomeMetric.avgUs());
        System.out.printf("  Region field lookups: %,d calls, %.2f ms total, %.2f µs avg%n",
            regionFieldMetric.calls(), regionFieldMetric.totalMs(), regionFieldMetric.avgUs());
        
        System.out.println("--- End Intermediate Stats ---\n");
    }
    
    private void printFinalSummary() {
        System.out.println("\n");
        Profiler.printSummary();
        
        // Also log the markdown version for easy copy/paste
        Terrasect.LOGGER.info("[ProfilerGameTest] Markdown Summary:\n{}", Profiler.getSummaryMarkdown());
        
        // Print allocation counts specifically
        System.out.println("\n--- Allocation Tracking ---");
        var allocClimate = Profiler.getMetric(Profiler.ALLOC_CLIMATE_RESULT);
        var allocTarget = Profiler.getMetric(Profiler.ALLOC_TARGET_POINT);
        var allocParams = Profiler.getMetric(Profiler.ALLOC_PARAMETER_LIST);
        
        System.out.printf("  ClimateResult allocations: %,d%n", allocClimate.calls());
        System.out.printf("  TargetPoint allocations: %,d%n", allocTarget.calls());
        System.out.printf("  ParameterList allocations: %,d%n", allocParams.calls());
        System.out.println("--- End Allocation Tracking ---\n");
        
        // Print per-location averages
        long totalCalls = Profiler.getMetric(Profiler.CLIMATE_MODIFY).calls();
        if (totalCalls > 0) {
            System.out.printf("Average climate modifications per location: %,d%n", 
                totalCalls / TELEPORT_LOCATIONS.length);
        }
    }
}
