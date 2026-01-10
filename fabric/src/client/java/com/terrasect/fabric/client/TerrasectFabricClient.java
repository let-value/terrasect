package com.terrasect.fabric.client;

import com.terrasect.common.gui.RegionDebugEntry;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.resources.Identifier;

/**
 * Client-side initialization for Terrasect.
 *
 * <p>Registers debug HUD entries to display region information when F3 is open.
 */
public class TerrasectFabricClient implements ClientModInitializer {

    public static final Identifier REGION_DEBUG =
            DebugScreenEntries.register(Identifier.fromNamespaceAndPath("terrasect", "region"), new RegionDebugEntry());

    @Override
    public void onInitializeClient() {
        // Debug entries are registered statically above
    }
}
