package com.terrasect.common.lookup;

import com.terrasect.common.Terrasect;
import com.terrasect.common.compat.ResourceKeyCompat;
import com.terrasect.common.definition.NoiseConstraints;
import com.terrasect.common.definition.NoiseTransform;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionDefinition;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jetbrains.annotations.Nullable;

public final class CompiledNoiseRegistry {
    private final IdentityHashMap<RegionDefinition, CompiledNoiseConstraints> constraints;

    private CompiledNoiseRegistry(IdentityHashMap<RegionDefinition, CompiledNoiseConstraints> constraints) {
        this.constraints = constraints;
    }

    public static CompiledNoiseRegistry build(Region root) {
        var map = new IdentityHashMap<RegionDefinition, CompiledNoiseConstraints>();
        compileRecursively(root, map);
        return new CompiledNoiseRegistry(map);
    }

    private static void compileRecursively(
            Region region, IdentityHashMap<RegionDefinition, CompiledNoiseConstraints> map) {
        var definition = region.definition();
        if (!map.containsKey(definition)) {
            var noise = definition.noise();
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

    public @Nullable CompiledNoiseConstraints get(RegionDefinition definition) {
        return constraints.get(definition);
    }

    public boolean isEmpty() {
        return constraints.isEmpty();
    }

    private static CompiledNoiseConstraints compileNoiseConstraints(NoiseConstraints constraints) {
        if (constraints == null) {
            return CompiledNoiseConstraints.EMPTY;
        }

        Map<String, NoiseTransform> noises = constraints.noises();
        if (noises.isEmpty()) {
            return CompiledNoiseConstraints.EMPTY;
        }

        var count = 0;
        for (var entry : noises.entrySet()) {
            var transform = entry.getValue();
            if (transform == null || transform.isEmpty()) continue;
            if (ResourceKeyCompat.tryParse(Registries.NOISE, entry.getKey()) == null) continue;
            count++;
        }

        if (count == 0) {
            return CompiledNoiseConstraints.EMPTY;
        }

        @SuppressWarnings("unchecked") var keys =
                (ResourceKey<NormalNoise.NoiseParameters>[]) new ResourceKey<?>[count];
        CompiledTransform[] transforms = new CompiledTransform[count];

        var i = 0;
        for (var entry : noises.entrySet()) {
            var transform = entry.getValue();
            if (transform == null || transform.isEmpty()) continue;

            var key = ResourceKeyCompat.tryParse(Registries.NOISE, entry.getKey());
            if (key == null) {
                Terrasect.LOGGER.warn("Invalid noise key '{}' in NoiseConstraints; skipping", entry.getKey());
                continue;
            }

            keys[i] = key;
            transforms[i] = CompiledTransform.compile(transform);
            i++;
        }

        if (i == 0) {
            return CompiledNoiseConstraints.EMPTY;
        }

        if (i != count) {
            @SuppressWarnings("unchecked") var trimmedKeys =
                    (ResourceKey<NormalNoise.NoiseParameters>[]) new ResourceKey<?>[i];
            CompiledTransform[] trimmedTransforms = new CompiledTransform[i];
            System.arraycopy(keys, 0, trimmedKeys, 0, i);
            System.arraycopy(transforms, 0, trimmedTransforms, 0, i);
            return new CompiledNoiseConstraints(trimmedKeys, trimmedTransforms);
        }

        return new CompiledNoiseConstraints(keys, transforms);
    }

    public static final class CompiledNoiseConstraints {
        static final CompiledNoiseConstraints EMPTY =
                new CompiledNoiseConstraints(new ResourceKey<?>[0], new CompiledTransform[0]);

        private final ResourceKey<NormalNoise.NoiseParameters>[] noiseKeys;
        private final CompiledTransform[] noiseTransforms;

        CompiledNoiseConstraints(ResourceKey<?>[] noiseKeys, CompiledTransform[] noiseTransforms) {
            @SuppressWarnings("unchecked") var typed = (ResourceKey<NormalNoise.NoiseParameters>[]) noiseKeys;
            this.noiseKeys = typed;
            this.noiseTransforms = noiseTransforms;
        }

        public boolean isEmpty() {
            return noiseKeys.length == 0;
        }

        public @Nullable CompiledTransform findNoiseTransform(ResourceKey<NormalNoise.NoiseParameters> key) {
            for (var i = 0; i < noiseKeys.length; i++) {
                if (noiseKeys[i] == key) {
                    return noiseTransforms[i];
                }
            }
            return null;
        }
    }

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
            for (var i = 0; i < ops.size(); i++) {
                out[i] = ops.get(i);
            }
            return new CompiledTransform(out);
        }

        public double apply(double value) {
            var out = value;
            for (var i = 0; i < operations.length; i++) {
                var op = operations[i];
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
                    var clamped = Mth.clamp(value, -1.0, 1.0);
                    yield clamped / 2.0 - clamped * clamped * clamped / 24.0;
                }
            };
        }
    }
}
