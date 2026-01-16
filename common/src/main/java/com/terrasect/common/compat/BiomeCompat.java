package com.terrasect.common.compat;

import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

public final class BiomeCompat {

  private BiomeCompat() {}

  public static String getBiomeId(Holder<Biome> biome) {
    return biome.unwrapKey().map(ResourceKeyCompat::getKeyId).orElse("unknown");
  }

  public static Stream<TagKey<Biome>> getTags(Holder<Biome> biome) {
    return biome.tags();
  }
}
