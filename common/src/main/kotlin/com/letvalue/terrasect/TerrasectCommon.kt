package com.letvalue.terrasect

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Common initialization logic shared between all loaders.
 * This class should be called from each loader's entry point.
 */
object TerrasectCommon {
    private val LOGGER: Logger = LoggerFactory.getLogger(TerrasectConstants.MOD_ID)

    /**
     * Called during common/shared mod initialization.
     */
    fun init() {
        LOGGER.info("Initializing ${TerrasectConstants.MOD_NAME} common...")
        
        // Register common items, blocks, etc. here
        // Each loader will call this during its initialization
    }
    
    /**
     * Called during client-side initialization.
     */
    fun initClient() {
        LOGGER.info("Initializing ${TerrasectConstants.MOD_NAME} client...")
        
        // Register client-side renderers, screens, etc. here
    }
}
