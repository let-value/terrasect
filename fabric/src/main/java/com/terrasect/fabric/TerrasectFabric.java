package com.terrasect.fabric;

import com.terrasect.common.Terrasect;
import net.fabricmc.api.ModInitializer;

public class TerrasectFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Terrasect.init();
    }
}
