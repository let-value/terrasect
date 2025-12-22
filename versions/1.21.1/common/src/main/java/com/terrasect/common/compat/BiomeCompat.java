package com.terrasect.common.compat;

import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.stream.Stream;

/**
 * VERSION OVERRIDE for 1.21.1: biome helper methods aligned with this MC API.
 */
public final class BiomeCompat {

    private BiomeCompat() {}

    public static String getBiomeId(Holder<Biome> biome) {
        return biome.unwrapKey()
            .map(ResourceKeyCompat::getKeyId)
            .orElse("unknown");
    }

    public static Stream<TagKey<Biome>> getTags(Holder<Biome> biome) {
        return biome.tags();
    }
}
