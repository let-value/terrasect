package com.terrasect.common;

import com.terrasect.common.generation.World;
import com.terrasect.common.generation.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Terrasect {
    public static final String MOD_ID = "terrasect";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Terrasect() {
    }

    public static void init() {
        LOGGER.info("Terrasect mod initialization started");
        
        // Initialize default empty world to prevent crashes
        // Users/Tests should override this
        World.setRoot(Region.builder("ROOT").build());
    }

    public static String hello() {
        return "Hello from Terrasect";
    }
}
