package com.terrasect.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Terrasect {
    public static final String MOD_ID = "terrasect";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Terrasect() {
    }

    public static void init() {
        LOGGER.info("Terrasect mod initialization started");
    }

    public static String hello() {
        return "Hello from Terrasect";
    }
}
