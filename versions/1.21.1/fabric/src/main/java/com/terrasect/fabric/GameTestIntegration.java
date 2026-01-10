package com.terrasect.fabric;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Server-side GameTest placeholder.
 *
 * VERSION OVERRIDE for 1.21.1: Uses Minecraft's GameTest annotation.
 *
 * NOTE: Server GameTest uses a FLAT WORLD (FlatLevelSource + FixedBiomeSource),
 * which bypasses MultiNoiseBiomeSource entirely. Our ClimateMixin won't be
 * called during server GameTest execution.
 *
 * For actual biome/climate testing with real world generation, use:
 * - Client GameTest: ./gradlew :mc1_21_1-fabric:runClientGameTest
 * - Manual testing: ./gradlew :mc1_21_1-fabric:runClient
 */
public class GameTestIntegration {

    /**
     * Placeholder test that always passes.
     * Real testing happens in ClientGameTestIntegration with actual world generation.
     */
    @GameTest(template = "terrasect:empty")
    public void serverGameTestPlaceholder(GameTestHelper helper) {
        System.out.println("=== Server GameTest (Flat World - No-op) ===");
        System.out.println("Server GameTest uses flat world which bypasses our biome mixins.");
        System.out.println("Use :mc1_21_1-fabric:runClientGameTest for real world generation tests.");
        helper.succeed();
    }
}
