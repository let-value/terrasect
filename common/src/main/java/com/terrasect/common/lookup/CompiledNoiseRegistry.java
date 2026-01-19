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
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jetbrains.annotations.Nullable;

public final class CompiledNoiseRegistry {
  private final IdentityHashMap<RegionDefinition, CompiledNoiseConstraints> constraints;

  private CompiledNoiseRegistry(
      IdentityHashMap<RegionDefinition, CompiledNoiseConstraints> constraints) {
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
    Map<String, NoiseTransform> densityFunctions = constraints.densityFunctions();
    if (noises.isEmpty() && densityFunctions.isEmpty()) {
      return CompiledNoiseConstraints.EMPTY;
    }

    var noiseCount = 0;
    for (var entry : noises.entrySet()) {
      var transform = entry.getValue();
      if (transform == null || transform.isEmpty()) continue;
      if (ResourceKeyCompat.tryParse(Registries.NOISE, entry.getKey()) == null) continue;
      noiseCount++;
    }

    var densityCount = 0;
    for (var entry : densityFunctions.entrySet()) {
      var transform = entry.getValue();
      if (transform == null || transform.isEmpty()) continue;
      if (ResourceKeyCompat.tryParse(Registries.DENSITY_FUNCTION, entry.getKey()) == null) continue;
      densityCount++;
    }

    if (noiseCount == 0 && densityCount == 0) {
      return CompiledNoiseConstraints.EMPTY;
    }

    @SuppressWarnings("unchecked")
    var noiseKeys = (ResourceKey<NormalNoise.NoiseParameters>[]) new ResourceKey<?>[noiseCount];
    CompiledTransform[] noiseTransforms = new CompiledTransform[noiseCount];
    @SuppressWarnings("unchecked")
    var densityKeys = (ResourceKey<DensityFunction>[]) new ResourceKey<?>[densityCount];
    CompiledTransform[] densityTransforms = new CompiledTransform[densityCount];

    var noiseIndex = 0;
    for (var entry : noises.entrySet()) {
      var transform = entry.getValue();
      if (transform == null || transform.isEmpty()) continue;

      var key = ResourceKeyCompat.tryParse(Registries.NOISE, entry.getKey());
      if (key == null) {
        Terrasect.LOGGER.warn(
            "Invalid noise key '{}' in NoiseConstraints; skipping", entry.getKey());
        continue;
      }

      noiseKeys[noiseIndex] = key;
      noiseTransforms[noiseIndex] = CompiledTransform.compile(transform);
      noiseIndex++;
    }

    var densityIndex = 0;
    for (var entry : densityFunctions.entrySet()) {
      var transform = entry.getValue();
      if (transform == null || transform.isEmpty()) continue;

      var key = ResourceKeyCompat.tryParse(Registries.DENSITY_FUNCTION, entry.getKey());
      if (key == null) {
        Terrasect.LOGGER.warn(
            "Invalid density function key '{}' in NoiseConstraints; skipping", entry.getKey());
        continue;
      }

      densityKeys[densityIndex] = key;
      densityTransforms[densityIndex] = CompiledTransform.compile(transform);
      densityIndex++;
    }

    if (noiseIndex == 0 && densityIndex == 0) {
      return CompiledNoiseConstraints.EMPTY;
    }

    var finalNoiseKeys = noiseKeys;
    var finalNoiseTransforms = noiseTransforms;
    if (noiseIndex != noiseCount) {
      @SuppressWarnings("unchecked")
      var trimmedKeys = (ResourceKey<NormalNoise.NoiseParameters>[]) new ResourceKey<?>[noiseIndex];
      CompiledTransform[] trimmedTransforms = new CompiledTransform[noiseIndex];
      System.arraycopy(noiseKeys, 0, trimmedKeys, 0, noiseIndex);
      System.arraycopy(noiseTransforms, 0, trimmedTransforms, 0, noiseIndex);
      finalNoiseKeys = trimmedKeys;
      finalNoiseTransforms = trimmedTransforms;
    }

    var finalDensityKeys = densityKeys;
    var finalDensityTransforms = densityTransforms;
    if (densityIndex != densityCount) {
      @SuppressWarnings("unchecked")
      var trimmedKeys = (ResourceKey<DensityFunction>[]) new ResourceKey<?>[densityIndex];
      CompiledTransform[] trimmedTransforms = new CompiledTransform[densityIndex];
      System.arraycopy(densityKeys, 0, trimmedKeys, 0, densityIndex);
      System.arraycopy(densityTransforms, 0, trimmedTransforms, 0, densityIndex);
      finalDensityKeys = trimmedKeys;
      finalDensityTransforms = trimmedTransforms;
    }

    return new CompiledNoiseConstraints(
        finalNoiseKeys, finalNoiseTransforms, finalDensityKeys, finalDensityTransforms);
  }

  public static final class CompiledNoiseConstraints {
    static final CompiledNoiseConstraints EMPTY =
        new CompiledNoiseConstraints(
            new ResourceKey<?>[0], new CompiledTransform[0], new ResourceKey<?>[0], new CompiledTransform[0]);

    private final ResourceKey<NormalNoise.NoiseParameters>[] noiseKeys;
    private final CompiledTransform[] noiseTransforms;
    private final ResourceKey<DensityFunction>[] densityKeys;
    private final CompiledTransform[] densityTransforms;

    CompiledNoiseConstraints(
        ResourceKey<?>[] noiseKeys,
        CompiledTransform[] noiseTransforms,
        ResourceKey<?>[] densityKeys,
        CompiledTransform[] densityTransforms) {
      @SuppressWarnings("unchecked")
      var typed = (ResourceKey<NormalNoise.NoiseParameters>[]) noiseKeys;
      this.noiseKeys = typed;
      this.noiseTransforms = noiseTransforms;
      @SuppressWarnings("unchecked")
      var typedDensity = (ResourceKey<DensityFunction>[]) densityKeys;
      this.densityKeys = typedDensity;
      this.densityTransforms = densityTransforms;
    }

    public boolean isEmpty() {
      return noiseKeys.length == 0 && densityKeys.length == 0;
    }

    public @Nullable CompiledTransform findNoiseTransform(
        ResourceKey<NormalNoise.NoiseParameters> key) {
      for (var i = 0; i < noiseKeys.length; i++) {
        if (noiseKeys[i] == key) {
          return noiseTransforms[i];
        }
      }
      return null;
    }

    public @Nullable CompiledTransform findDensityTransform(ResourceKey<DensityFunction> key) {
      for (var i = 0; i < densityKeys.length; i++) {
        if (densityKeys[i] == key) {
          return densityTransforms[i];
        }
      }
      return null;
    }
  }

  public static final class CompiledTransform {
    private static final CompiledTransform EMPTY =
        new CompiledTransform(new NoiseTransform.Operation[0]);

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
