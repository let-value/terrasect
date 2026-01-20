package com.letvalue.terrasect

import com.letvalue.terrasect.TerrasectCommon
import com.letvalue.terrasect.TerrasectConstants
import com.mojang.logging.LogUtils
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartingEvent

/**
 * Main mod class for NeoForge.
 * The value here should match an entry in the META-INF/neoforge.mods.toml file.
 */
@Mod(TerrasectConstants.MOD_ID)
class Terrasect(modEventBus: IEventBus, modContainer: ModContainer) {
    
    companion object {
        private val LOGGER = LogUtils.getLogger()
    }

    init {
        // Register the commonSetup method for mod loading
        modEventBus.addListener(::commonSetup)
        modEventBus.addListener(::clientSetup)

        // Register ourselves for server and other game events we are interested in
        NeoForge.EVENT_BUS.register(this)
        
        LOGGER.info("Hello from ${TerrasectConstants.MOD_NAME} on NeoForge!")
    }

    private fun commonSetup(event: FMLCommonSetupEvent) {
        // Some common setup code
        LOGGER.info("${TerrasectConstants.MOD_NAME} common setup")
        
        // Initialize common code
        TerrasectCommon.init()
    }
    
    private fun clientSetup(event: FMLClientSetupEvent) {
        LOGGER.info("${TerrasectConstants.MOD_NAME} client setup")
        LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().user.name)
        
        // Initialize common client code
        TerrasectCommon.initClient()
    }

    @SubscribeEvent
    fun onServerStarting(event: ServerStartingEvent) {
        // Do something when the server starts
        LOGGER.info("${TerrasectConstants.MOD_NAME} server starting")
    }
}
