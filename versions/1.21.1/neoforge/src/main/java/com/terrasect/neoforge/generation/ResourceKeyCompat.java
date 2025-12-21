package com.terrasect.neoforge.generation;

import net.minecraft.resources.ResourceKey;

/**
 * Version compatibility helper for NeoForge API differences.
 * 
 * <p>VERSION OVERRIDE for 1.21.1: Uses location() instead of identifier().
 */
public final class ResourceKeyCompat {
    
    private ResourceKeyCompat() {}
    
    /**
     * Get the ResourceLocation string from a ResourceKey.
     * 
     * <p>1.21.1 uses location()
     */
    public static String getKeyId(ResourceKey<?> key) {
        return key.location().toString();
    }
}
