package com.terrasect.fabric;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Server-side GameTest placeholder.
 * 
 * NOTE: Server GameTest uses a FLAT WORLD (FlatLevelSource + FixedBiomeSource),
 * which bypasses MultiNoiseBiomeSource entirely. Our ClimateMixin won't be
 * called during server GameTest execution.
 * 
 * For actual biome/climate testing with real world generation, use:
 * - Client GameTest: ./gradlew :fabric:runClientGameTest
 * - Manual testing: ./gradlew :fabric:runClient
 * 
 * @see com.terrasect.fabric.client.ClientGameTestIntegration
 */
public class GameTestIntegration {

    /**
     * Placeholder test that always passes.
     * Real testing happens in ClientGameTestIntegration with actual world generation.
     */
    @GameTest
    public void serverGameTestPlaceholder(GameTestHelper helper) {
        System.out.println("=== Server GameTest (Flat World - No-op) ===");
        System.out.println("Server GameTest uses flat world which bypasses our biome mixins.");
        System.out.println("Use :fabric:runClientGameTest for real world generation tests.");
        helper.succeed();
    }
}
