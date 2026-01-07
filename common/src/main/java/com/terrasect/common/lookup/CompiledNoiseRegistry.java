package com.terrasect.common.lookup;

import com.terrasect.common.Terrasect;
import com.terrasect.common.definition.NoiseConstraints;
import com.terrasect.common.definition.NoiseTransform;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionDefinition;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pre-compiled noise constraints for all regions in a dimension.
 *
 * <p>Built once when a region hierarchy is registered, then used by
 * {@link NoiseChunkLookup} for O(1) constraint lookups.
 */
public final class CompiledNoiseRegistry {
    private final IdentityHashMap<RegionDefinition, CompiledNoiseConstraints> constraints;

    private CompiledNoiseRegistry(IdentityHashMap<RegionDefinition, CompiledNoiseConstraints> constraints) {
        this.constraints = constraints;
    }

    /**
     * Build a registry by traversing the entire region tree and compiling all noise constraints.
     */
    public static CompiledNoiseRegistry build(Region root) {
        IdentityHashMap<RegionDefinition, CompiledNoiseConstraints> map = new IdentityHashMap<>();
        compileRecursively(root, map);
        return new CompiledNoiseRegistry(map);
    }

    private static void compileRecursively(Region region, IdentityHashMap<RegionDefinition, CompiledNoiseConstraints> map) {
        RegionDefinition definition = region.definition();
        if (!map.containsKey(definition)) {
            NoiseConstraints noise = definition.noise();
            if (noise != null && noise.hasAnyConstraints()) {
                CompiledNoiseConstraints compiled = compileNoiseConstraints(noise);
                if (!compiled.isEmpty()) {
                    map.put(definition, compiled);
                }
            }
        }

        for (Region child : region.children()) {
            compileRecursively(child, map);
        }
    }

    /**
     * Get compiled constraints for a region definition, or {@code null} if none.
     */
    public @Nullable CompiledNoiseConstraints get(RegionDefinition definition) {
        return constraints.get(definition);
    }

    /**
     * Check if any constraints are registered.
     */
    public boolean isEmpty() {
        return constraints.isEmpty();
    }

    // --- Compilation ---

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

    // --- Inner classes ---

    /**
     * Compiled noise constraints for a region, holding transform operations keyed by noise parameter.
     */
    public static final class CompiledNoiseConstraints {
        static final CompiledNoiseConstraints EMPTY = new CompiledNoiseConstraints(new ResourceKey<?>[0], new CompiledTransform[0]);

        private final ResourceKey<NormalNoise.NoiseParameters>[] noiseKeys;
        private final CompiledTransform[] noiseTransforms;

        CompiledNoiseConstraints(ResourceKey<?>[] noiseKeys, CompiledTransform[] noiseTransforms) {
            @SuppressWarnings("unchecked")
            ResourceKey<NormalNoise.NoiseParameters>[] typed = (ResourceKey<NormalNoise.NoiseParameters>[]) noiseKeys;
            this.noiseKeys = typed;
            this.noiseTransforms = noiseTransforms;
        }

        public boolean isEmpty() {
            return noiseKeys.length == 0;
        }

        public @Nullable CompiledTransform findNoiseTransform(ResourceKey<NormalNoise.NoiseParameters> key) {
            for (int i = 0; i < noiseKeys.length; i++) {
                if (noiseKeys[i] == key) {
                    return noiseTransforms[i];
                }
            }
            return null;
        }
    }

    /**
     * Compiled sequence of transform operations to apply to a noise value.
     */
    public static final class CompiledTransform {
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

        public double apply(double value) {
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
