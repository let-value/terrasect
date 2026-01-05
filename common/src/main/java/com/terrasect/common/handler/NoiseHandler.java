package com.terrasect.common.handler;

import com.terrasect.common.Context;
import com.terrasect.common.Terrasect;
import com.terrasect.common.compat.NoiseChunkNoiseAccess;
import com.terrasect.common.definition.NoiseConstraints;
import com.terrasect.common.definition.NoiseTransform;
import com.terrasect.common.definition.RegionDefinition;
import com.terrasect.common.generation.TraversalResult;
import com.terrasect.common.generation.World;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared noise modification logic for platform mixins.
 *
 * <p>Hot paths must not allocate. Region lookups are built once per {@code NoiseChunk} and then
 * reused by redirects on vanilla density-function noise sampling.
 */
public final class NoiseHandler {
    private static final int CHUNK_SIZE = 16;
    private static final int QUART_SHIFT = 2;
    private static final int QUART_SIZE = CHUNK_SIZE >> QUART_SHIFT; // 4
    private static final int QUART_MASK = QUART_SIZE - 1; // 3

    private static final ThreadLocal<IdentityHashMap<RegionDefinition, CompiledNoiseConstraints>> COMPILED_BY_DEFINITION =
            ThreadLocal.withInitial(IdentityHashMap::new);

    private NoiseHandler() {}

    /**
     * Precompute region noise constraints for a chunk at quart (4-block) resolution.
     *
     * <p>This runs once per {@code NoiseChunk} and may allocate; sampling must not.
     */
    public static @Nullable NoiseChunkLookup buildLookup(@Nullable Context context, int chunkMinX, int chunkMinZ) {
        if (context == null) {
            return null;
        }

        CompiledNoiseConstraints[] constraints = new CompiledNoiseConstraints[QUART_SIZE * QUART_SIZE];
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
                NoiseConstraints noise = definition.noise();
                if (noise == null || !noise.hasAnyConstraints()) {
                    strengths[index] = 0.0f;
                    continue;
                }

                CompiledNoiseConstraints compiled = compile(definition);
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
     * Sample a vanilla {@link DensityFunction.NoiseHolder} value and apply region constraints.
     *
     * <p>This must not allocate.
     */
    public static double sampleNoise(
            DensityFunction.NoiseHolder noiseHolder,
            double x, double y, double z,
            DensityFunction.FunctionContext functionContext) {

        double original = noiseHolder.getValue(x, y, z);
        return applyNoiseConstraints(noiseHolder, functionContext, original);
    }

    private static double applyNoiseConstraints(
            DensityFunction.NoiseHolder noiseHolder,
            DensityFunction.FunctionContext functionContext,
            double original) {

        if (!(functionContext instanceof NoiseChunkNoiseAccess access)) {
            return original;
        }
        NoiseChunkLookup lookup = access.terrasect$getNoiseLookup();
        if (lookup == null) {
            return original;
        }

        int blockX = functionContext.blockX();
        int blockZ = functionContext.blockZ();
        int index = lookup.index(blockX, blockZ);
        if (index < 0) {
            return original;
        }

        CompiledNoiseConstraints constraints = lookup.constraints[index];
        if (constraints == null) {
            return original;
        }

        Holder<NormalNoise.NoiseParameters> noiseData = noiseHolder.noiseData();
        if (!(noiseData instanceof Holder.Reference<NormalNoise.NoiseParameters> ref)) {
            return original;
        }

        ResourceKey<NormalNoise.NoiseParameters> key = ref.key();
        CompiledTransform transform = constraints.findNoiseTransform(key);
        if (transform == null) {
            return original;
        }

        float strength = lookup.strengths[index];
        if (strength <= 0.0f) {
            return original;
        }

        double transformed = transform.apply(original);
        if (strength >= 1.0f) {
            return transformed;
        }

        return original + (transformed - original) * strength;
    }

    private static CompiledNoiseConstraints compile(RegionDefinition definition) {
        IdentityHashMap<RegionDefinition, CompiledNoiseConstraints> cache = COMPILED_BY_DEFINITION.get();
        CompiledNoiseConstraints cached = cache.get(definition);
        if (cached != null) {
            return cached;
        }

        NoiseConstraints constraints = definition.noise();
        CompiledNoiseConstraints compiled = compileNoiseConstraints(constraints);
        cache.put(definition, compiled);
        return compiled;
    }

    private static CompiledNoiseConstraints compileNoiseConstraints(NoiseConstraints constraints) {
        if (constraints == null) {
            return CompiledNoiseConstraints.EMPTY;
        }

        Map<String, NoiseTransform> noises = constraints.noises();
        if (noises.isEmpty()) {
            return CompiledNoiseConstraints.EMPTY;
        }

        int count = 0;
        for (var entry : noises.entrySet()) {
            NoiseTransform transform = entry.getValue();
            if (transform == null || transform.isEmpty()) continue;
            Identifier id = Identifier.tryParse(entry.getKey());
            if (id == null) continue;
            count++;
        }

        if (count == 0) {
            return CompiledNoiseConstraints.EMPTY;
        }

        @SuppressWarnings("unchecked")
        ResourceKey<NormalNoise.NoiseParameters>[] keys = (ResourceKey<NormalNoise.NoiseParameters>[]) new ResourceKey<?>[count];
        CompiledTransform[] transforms = new CompiledTransform[count];

        int i = 0;
        for (var entry : noises.entrySet()) {
            NoiseTransform transform = entry.getValue();
            if (transform == null || transform.isEmpty()) continue;

            Identifier id = Identifier.tryParse(entry.getKey());
            if (id == null) {
                Terrasect.LOGGER.warn("Invalid noise key '{}' in NoiseConstraints; skipping", entry.getKey());
                continue;
            }

            keys[i] = ResourceKey.create(Registries.NOISE, id);
            transforms[i] = CompiledTransform.compile(transform);
            i++;
        }

        if (i == 0) {
            return CompiledNoiseConstraints.EMPTY;
        }

        // If some keys were invalid, 'i' may be < count. That only happens on misconfiguration and is still cold.
        if (i != count) {
            @SuppressWarnings("unchecked")
            ResourceKey<NormalNoise.NoiseParameters>[] trimmedKeys = (ResourceKey<NormalNoise.NoiseParameters>[]) new ResourceKey<?>[i];
            CompiledTransform[] trimmedTransforms = new CompiledTransform[i];
            System.arraycopy(keys, 0, trimmedKeys, 0, i);
            System.arraycopy(transforms, 0, trimmedTransforms, 0, i);
            return new CompiledNoiseConstraints(trimmedKeys, trimmedTransforms);
        }

        return new CompiledNoiseConstraints(keys, transforms);
    }

    public static final class NoiseChunkLookup {
        final CompiledNoiseConstraints[] constraints;
        final float[] strengths;
        private final int chunkMinX;
        private final int chunkMinZ;

        NoiseChunkLookup(CompiledNoiseConstraints[] constraints, float[] strengths, int chunkMinX, int chunkMinZ) {
            this.constraints = constraints;
            this.strengths = strengths;
            this.chunkMinX = chunkMinX;
            this.chunkMinZ = chunkMinZ;
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

    static final class CompiledNoiseConstraints {
        static final CompiledNoiseConstraints EMPTY = new CompiledNoiseConstraints(new ResourceKey<?>[0], new CompiledTransform[0]);

        private final ResourceKey<NormalNoise.NoiseParameters>[] noiseKeys;
        private final CompiledTransform[] noiseTransforms;

        CompiledNoiseConstraints(ResourceKey<?>[] noiseKeys, CompiledTransform[] noiseTransforms) {
            @SuppressWarnings("unchecked")
            ResourceKey<NormalNoise.NoiseParameters>[] typed = (ResourceKey<NormalNoise.NoiseParameters>[]) noiseKeys;
            this.noiseKeys = typed;
            this.noiseTransforms = noiseTransforms;
        }

        boolean isEmpty() {
            return noiseKeys.length == 0;
        }

        @Nullable CompiledTransform findNoiseTransform(ResourceKey<NormalNoise.NoiseParameters> key) {
            for (int i = 0; i < noiseKeys.length; i++) {
                if (noiseKeys[i] == key) {
                    return noiseTransforms[i];
                }
            }
            return null;
        }
    }

    static final class CompiledTransform {
        private static final CompiledTransform EMPTY = new CompiledTransform(new NoiseTransform.Operation[0]);

        private final NoiseTransform.Operation[] operations;

        private CompiledTransform(NoiseTransform.Operation[] operations) {
            this.operations = operations;
        }

        static CompiledTransform compile(NoiseTransform transform) {
            if (transform == null || transform.isEmpty()) {
                return EMPTY;
            }

            List<NoiseTransform.Operation> ops = transform.operations();
            NoiseTransform.Operation[] out = new NoiseTransform.Operation[ops.size()];
            for (int i = 0; i < ops.size(); i++) {
                out[i] = ops.get(i);
            }
            return new CompiledTransform(out);
        }

        double apply(double value) {
            double out = value;
            for (int i = 0; i < operations.length; i++) {
                NoiseTransform.Operation op = operations[i];
                if (op instanceof NoiseTransform.Clamp clamp) {
                    out = Mth.clamp(out, clamp.min(), clamp.max());
                } else if (op instanceof NoiseTransform.Add add) {
                    out += add.value();
                } else if (op instanceof NoiseTransform.Multiply multiply) {
                    out *= multiply.value();
                } else if (op instanceof NoiseTransform.Map map) {
                    out = applyMap(map.type(), out);
                }
            }
            return out;
        }

        private static double applyMap(NoiseTransform.MapType type, double value) {
            return switch (type) {
                case ABS -> Math.abs(value);
                case SQUARE -> value * value;
                case CUBE -> value * value * value;
                case HALF_NEGATIVE -> value > 0.0 ? value : value * 0.5;
                case QUARTER_NEGATIVE -> value > 0.0 ? value : value * 0.25;
                case INVERT -> 1.0 / value;
                case SQUEEZE -> {
                    double clamped = Mth.clamp(value, -1.0, 1.0);
                    yield clamped / 2.0 - clamped * clamped * clamped / 24.0;
                }
            };
        }
    }
}

