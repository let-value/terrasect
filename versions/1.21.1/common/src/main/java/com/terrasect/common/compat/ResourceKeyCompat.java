package com.terrasect.common.compat;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Version compatibility helper for Minecraft API differences.
 * 
 * <p>VERSION OVERRIDE for 1.21.1: Uses location()/ResourceLocation instead of identifier()/Identifier.
 */
public final class ResourceKeyCompat {
    
    private ResourceKeyCompat() {}
    
    /**
     * Get the ResourceLocation string from a ResourceKey.
     */
    public static String getKeyId(ResourceKey<?> key) {
        return key.location().toString();
    }

    /**
     * Parse a string as a ResourceKey for a given registry, or null if invalid.
     */
    public static @Nullable <T> ResourceKey<T> tryParse(ResourceKey<? extends Registry<T>> registry, String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        return parsed != null ? ResourceKey.create(registry, parsed) : null;
    }
}
