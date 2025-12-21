package com.terrasect.neoforge.generation;

import net.minecraft.resources.ResourceKey;

/**
 * Version compatibility helper for NeoForge API differences.
 * 
 * <p>Override this class in versioned projects to handle API changes
 * between NeoForge versions.
 * 
 * <p>1.21.11+: Uses identifier() on ResourceKey
 * <p>1.21.1 and earlier: Uses location() on ResourceKey
 */
public final class ResourceKeyCompat {
    
    private ResourceKeyCompat() {}
    
    /**
     * Get the ResourceLocation string from a ResourceKey.
     * 
     * <p>API difference:
     * <ul>
     *   <li>1.21.11+: ResourceKey.identifier()</li>
     *   <li>1.21.1-: ResourceKey.location()</li>
     * </ul>
     */
    public static String getKeyId(ResourceKey<?> key) {
        // 1.21.11+ uses identifier()
        return key.identifier().toString();
    }
}
