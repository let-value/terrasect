package com.terrasect.common.lookup;

import com.terrasect.common.Context;
import com.terrasect.common.definition.RegionDefinition;
import com.terrasect.common.generation.TraversalResult;
import com.terrasect.common.generation.World;
import org.jetbrains.annotations.Nullable;

/**
 * Precomputed region noise constraints for a chunk at quart (4-block) resolution.
 *
 * <p>Built once per {@code NoiseChunk} via {@link #build}, then attached to the chunk
 * for use during noise sampling.
 */
public final class NoiseChunkLookup {
    private static final int CHUNK_SIZE = 16;
    private static final int QUART_SHIFT = 2;
    private static final int QUART_SIZE = CHUNK_SIZE >> QUART_SHIFT; // 4
    private static final int QUART_MASK = QUART_SIZE - 1; // 3

    private final CompiledNoiseRegistry.CompiledNoiseConstraints[] constraints;
    private final float[] strengths;
    private final int chunkMinX;
    private final int chunkMinZ;

    private NoiseChunkLookup(
            CompiledNoiseRegistry.CompiledNoiseConstraints[] constraints,
            float[] strengths,
            int chunkMinX,
            int chunkMinZ) {
        this.constraints = constraints;
        this.strengths = strengths;
        this.chunkMinX = chunkMinX;
        this.chunkMinZ = chunkMinZ;
    }

    /**
     * Build a lookup for the given chunk. May allocate; sampling must not.
     *
     * @param context The generation context
     * @param chunkMinX Chunk minimum X coordinate (block units)
     * @param chunkMinZ Chunk minimum Z coordinate (block units)
     * @return the lookup, or {@code null} if no regions define noise constraints for this chunk
     */
    public static @Nullable NoiseChunkLookup build(@Nullable Context context, int chunkMinX, int chunkMinZ) {
        if (context == null) {
            return null;
        }

        CompiledNoiseRegistry registry = World.getNoiseRegistry(context.getDimensionId());
        if (registry == null || registry.isEmpty()) {
            return null;
        }

        CompiledNoiseRegistry.CompiledNoiseConstraints[] constraints =
                new CompiledNoiseRegistry.CompiledNoiseConstraints[QUART_SIZE * QUART_SIZE];
        float[] strengths = new float[QUART_SIZE * QUART_SIZE];
        boolean hasAny = false;

        for (int qz = 0; qz < QUART_SIZE; qz++) {
            int blockZ = chunkMinZ + (qz << QUART_SHIFT);
            for (int qx = 0; qx < QUART_SIZE; qx++) {
                int blockX = chunkMinX + (qx << QUART_SHIFT);
                int index = qx + (qz << QUART_SHIFT);

                TraversalResult traversal = World.traverse(context, blockX, blockZ);
                if (traversal == null || traversal.region == null) {
                    strengths[index] = 0.0f;
                    continue;
                }

                RegionDefinition definition = traversal.region.definition();
                CompiledNoiseRegistry.CompiledNoiseConstraints compiled = registry.get(definition);
                if (compiled == null || compiled.isEmpty()) {
                    strengths[index] = 0.0f;
                    continue;
                }

                constraints[index] = compiled;
                strengths[index] = 1.0f - traversal.edgeInfluence;
                hasAny = true;
            }
        }

        return hasAny ? new NoiseChunkLookup(constraints, strengths, chunkMinX, chunkMinZ) : null;
    }

    /**
     * Get the compiled noise constraints at the given block position, or {@code null} if none.
     */
    public @Nullable CompiledNoiseRegistry.CompiledNoiseConstraints getConstraints(int blockX, int blockZ) {
        int index = index(blockX, blockZ);
        return index >= 0 ? constraints[index] : null;
    }

    /**
     * Get the blend strength at the given block position, or 0 if out of bounds.
     */
    public float getStrength(int blockX, int blockZ) {
        int index = index(blockX, blockZ);
        return index >= 0 ? strengths[index] : 0.0f;
    }

    int index(int blockX, int blockZ) {
        int localX = (blockX - chunkMinX) >> QUART_SHIFT;
        int localZ = (blockZ - chunkMinZ) >> QUART_SHIFT;
        if ((localX & ~QUART_MASK) != 0 || (localZ & ~QUART_MASK) != 0) {
            return -1;
        }
        return localX + (localZ << QUART_SHIFT);
    }
}
