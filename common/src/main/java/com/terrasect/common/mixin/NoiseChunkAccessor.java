package com.terrasect.common.mixin;

import com.terrasect.common.lookup.NoiseChunkLookup;
import org.jetbrains.annotations.Nullable;

public interface NoiseChunkAccessor {
  @Nullable NoiseChunkLookup terrasect$getNoiseLookup();
}
