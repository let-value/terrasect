package com.terrasect.common;

import com.terrasect.common.api.Region;
import com.terrasect.common.api.RegionRegistry;
import com.terrasect.common.devtools.TestRegions;
import com.terrasect.common.runtime.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Terrasect {
    public static final String MOD_ID = "terrasect";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    /**
     * Set to true to load test regions on mod initialization.
     * This is useful for development and testing.
     */
    private static boolean useTestRegions = true;

    private Terrasect() {
    }

    public static void init() {
        LOGGER.info("Terrasect mod initialization started");

        if (useTestRegions) {
            // Load test regions for development (now dimension-aware)
            TestRegions.register();
            LOGGER.info("Terrasect initialized with TEST regions for {} dimensions", 
                World.getRegisteredDimensions().size());
        } else {
            // Initialize default empty world
            // Users/Tests should override this with their own configuration
            RegionRegistry registry = new RegionRegistry();
            registry.region("ROOT");
            Region root = registry.build("ROOT");
            
            // Register for Overworld
            World.register(World.OVERWORLD, root);
            
            LOGGER.info("Terrasect initialized with empty root region");
        }
    }
    
    /**
     * Enable or disable test regions.
     * Call this before init() if you want to change the default behavior.
     */
    public static void setUseTestRegions(boolean use) {
        useTestRegions = use;
    }

    public static String hello() {
        return "Hello from Terrasect";
    }
}
