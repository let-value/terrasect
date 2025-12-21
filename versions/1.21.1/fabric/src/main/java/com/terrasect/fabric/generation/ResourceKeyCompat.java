package com.terrasect.fabric.generation;

import net.minecraft.resources.ResourceKey;

/**
 * Version compatibility helper for Fabric API differences.
 * 
 * <p>VERSION OVERRIDE for 1.21.1 and earlier: Uses location() instead of identifier().
 */
public final class ResourceKeyCompat {
    
    private ResourceKeyCompat() {}
    
    /**
     * Get the ResourceLocation string from a ResourceKey.
     * 
     * <p>1.21.1 and earlier use location()
     */
    public static String getKeyId(ResourceKey<?> key) {
        return key.location().toString();
    }
}
