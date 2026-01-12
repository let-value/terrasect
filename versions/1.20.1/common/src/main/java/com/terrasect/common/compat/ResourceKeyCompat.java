package com.terrasect.common.compat;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class ResourceKeyCompat {

    private ResourceKeyCompat() {}

    public static String getKeyId(ResourceKey<?> key) {
        return key.location().toString();
    }

    public static @Nullable <T> ResourceKey<T> tryParse(ResourceKey<? extends Registry<T>> registry, String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        return parsed != null ? ResourceKey.create(registry, parsed) : null;
    }
}
