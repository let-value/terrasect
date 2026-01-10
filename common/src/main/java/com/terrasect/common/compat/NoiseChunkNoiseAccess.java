package com.terrasect.common.compat;

import com.terrasect.common.lookup.NoiseChunkLookup;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented via mixin on {@code NoiseChunk} to expose a precomputed per-chunk noise-constraint lookup.
 */
public interface NoiseChunkNoiseAccess {
    @Nullable NoiseChunkLookup terrasect$getNoiseLookup();

    void terrasect$setNoiseLookup(@Nullable NoiseChunkLookup lookup);
}
