package com.terrasect.fabric;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class GameTestIntegration {

  @GameTest(template = "terrasect:empty")
  public void serverGameTestPlaceholder(GameTestHelper helper) {
    System.out.println("=== Server GameTest (Flat World - No-op) ===");
    System.out.println("Server GameTest uses flat world which bypasses our biome mixins.");
    System.out.println("Use :mc1_21_1-fabric:runClientGameTest for real world generation tests.");
    helper.succeed();
  }
}
