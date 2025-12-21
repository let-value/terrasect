package com.terrasect.fabric.client;

import com.terrasect.common.devtools.MixinSampler;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

public class ClientGameTestIntegration implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        System.out.println("=== Client GameTest Starting ===");
        System.out.println("Creating a NORMAL world to verify MixinSampler matches reality...");

        // Enable sampling with high frequency to catch more samples
        MixinSampler.clear();
        MixinSampler.setSampleRate(1); // Record EVERY call
        MixinSampler.enable();

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

            // Take initial screenshot
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(5);
            context.takeScreenshot("01_spawn_overview");
        }

        MixinSampler.disable();
    }

}
