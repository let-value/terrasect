package com.terrasect.fabric.generation;

import net.minecraft.resources.ResourceKey;

/**
 * Version compatibility helper for Fabric API differences.
 * 
 * <p>This class isolates version-specific API calls so they can be overridden
 * in versioned subprojects using partial class overrides.
 * 
 * <p>1.21.11+ uses identifier(), older versions use location().
 */
public final class ResourceKeyCompat {
    
    private ResourceKeyCompat() {}
    
    /**
     * Get the ResourceLocation string from a ResourceKey.
     * 
     * <p>1.21.11+ uses identifier()
     */
    public static String getKeyId(ResourceKey<?> key) {
        return key.identifier().toString();
    }
}
