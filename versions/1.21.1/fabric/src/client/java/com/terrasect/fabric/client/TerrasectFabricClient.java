package com.terrasect.fabric.client;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client-side initialization for Terrasect (1.21.1 version).
 *
 * <p>The debug HUD entries (DebugScreenEntries) are not available in 1.21.1,
 * so this is a minimal stub implementation.
 */
public class TerrasectFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Debug HUD registration not available in 1.21.1
    }
}
