package com.letvalue.terrasect

import com.letvalue.terrasect.TerrasectCommon
import com.letvalue.terrasect.TerrasectConstants
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object Terrasect : ModInitializer {
    private val logger = LoggerFactory.getLogger(TerrasectConstants.MOD_ID)

    override fun onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.
        logger.info("Hello from ${TerrasectConstants.MOD_NAME} on Fabric!")
        
        // Initialize common code
        TerrasectCommon.init()
    }
}
