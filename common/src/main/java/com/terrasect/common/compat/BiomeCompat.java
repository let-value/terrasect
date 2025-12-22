package com.terrasect.common.compat;

import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.stream.Stream;

/**
 * Compatibility helpers for accessing biome metadata across Minecraft versions.
 */
public final class BiomeCompat {

    private BiomeCompat() {}

    /**
     * Get the string ID for a biome holder.
     */
    public static String getBiomeId(Holder<Biome> biome) {
        return biome.unwrapKey()
            .map(ResourceKeyCompat::getKeyId)
            .orElse("unknown");
    }

    /**
     * Access biome tags in a version-stable way.
     */
    public static Stream<TagKey<Biome>> getTags(Holder<Biome> biome) {
        return biome.tags();
    }
}
