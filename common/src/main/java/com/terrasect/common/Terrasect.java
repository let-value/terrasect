package com.terrasect.common;

import com.terrasect.common.definition.RegionRegistry;
import com.terrasect.common.generation.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Terrasect {
    public static final String MOD_ID = "terrasect";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static boolean useTestRegions = true;

    private Terrasect() {
    }

    public static void init() {
        LOGGER.info("Terrasect mod initialization started");

        if (useTestRegions) {

            TestRegions.register();
            LOGGER.info(
                    "Terrasect initialized with TEST regions for {} dimensions",
                    World.getRegisteredDimensions().size());
        } else {

            var registry = new RegionRegistry();
            registry.region("ROOT");
            var root = registry.build("ROOT");

            World.register(root, World.OVERWORLD);

            LOGGER.info("Terrasect initialized with empty root region");
        }
    }

    public static void setUseTestRegions(boolean use) {
        useTestRegions = use;
    }

    public static String hello() {
        return "Hello from Terrasect";
    }
}
