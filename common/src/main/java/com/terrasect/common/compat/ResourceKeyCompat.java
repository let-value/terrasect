package com.terrasect.common.compat;

import net.minecraft.resources.ResourceKey;

public final class ResourceKeyCompat {
    
    private ResourceKeyCompat() {}

    public static String getKeyId(ResourceKey<?> key) {
        return key.location().toString();
    }
}
