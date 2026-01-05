package com.terrasect.common.compat;

import com.terrasect.common.handler.NoiseHandler;

import org.jetbrains.annotations.Nullable;

/**
 * Implemented via mixin on {@code NoiseChunk} to expose a precomputed per-chunk noise-constraint lookup.
 */
public interface NoiseChunkNoiseAccess {
    @Nullable NoiseHandler.NoiseChunkLookup terrasect$getNoiseLookup();

    void terrasect$setNoiseLookup(@Nullable NoiseHandler.NoiseChunkLookup lookup);
}

