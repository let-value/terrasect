package com.terrasect;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Terrasect.MOD_ID)
public class Terrasect {
    public static final String MOD_ID = "terrasect";
    private static final Logger LOGGER = LoggerFactory.getLogger(Terrasect.class);
    
    public Terrasect(IEventBus modEventBus) {
        LOGGER.info("Terrasect mod initializing - Single biome worldgen enabled");
        
        // Register event listeners
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
    }
    
    private void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Terrasect: Server starting with single biome worldgen");
    }
}
