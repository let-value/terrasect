package com.terrasect.common.compat;

import com.terrasect.common.lookup.NoiseChunkLookup;
import org.jetbrains.annotations.Nullable;

public interface NoiseChunkNoiseAccess {
    @Nullable NoiseChunkLookup terrasect$getNoiseLookup();

    void terrasect$setNoiseLookup(@Nullable NoiseChunkLookup lookup);
}
