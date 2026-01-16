package com.terrasect.common.compat;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

public final class ResourceKeyCompat {

  private ResourceKeyCompat() {}

  public static String getKeyId(ResourceKey<?> key) {
    return key.identifier().toString();
  }

  public static @Nullable <T> ResourceKey<T> tryParse(
      ResourceKey<? extends Registry<T>> registry, String id) {
    Identifier parsed = Identifier.tryParse(id);
    return parsed != null ? ResourceKey.create(registry, parsed) : null;
  }
}
