package com.terrasect.fabric;

import com.terrasect.common.Terrasect;
import com.terrasect.fabric.narrgen.NarrGenCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class TerrasectFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Terrasect.init();
        
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            NarrGenCommand.register(dispatcher);
        });
    }
}
