package com.terrasect.common.definition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public record NoiseConstraints(
    Map<String, NoiseTransform> noises,
    Map<String, NoiseTransform> densityFunctions,
    boolean clearsParent) {

  public NoiseConstraints {
    if (noises == null) noises = Collections.emptyMap();
    if (densityFunctions == null) densityFunctions = Collections.emptyMap();
    noises = Map.copyOf(noises);
    densityFunctions = Map.copyOf(densityFunctions);
  }

  public boolean hasAnyConstraints() {
    return !noises.isEmpty() || !densityFunctions.isEmpty();
  }

  public static NoiseConstraints empty() {
    return new NoiseConstraints(Collections.emptyMap(), Collections.emptyMap(), false);
  }

  public NoiseConstraints resolveWithParent(NoiseConstraints parent) {
    if (clearsParent) return this;
    if (parent == null) return this;
    if (!parent.hasAnyConstraints()) return this;

    var mergedNoises = new LinkedHashMap<String, NoiseTransform>(parent.noises);
    mergedNoises.putAll(noises);

    var mergedDensity = new LinkedHashMap<String, NoiseTransform>(parent.densityFunctions);
    mergedDensity.putAll(densityFunctions);

    return new NoiseConstraints(mergedNoises, mergedDensity, false);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private boolean clearParent = false;
    private final Map<String, NoiseTransform> noises = new LinkedHashMap<>();
    private final Map<String, NoiseTransform> densityFunctions = new LinkedHashMap<>();

    public Builder clearParent() {
      clearParent = true;
      return this;
    }

    public Builder noise(String noiseKey, Consumer<NoiseTransform.Builder> consumer) {
      Objects.requireNonNull(noiseKey, "noiseKey");
      Objects.requireNonNull(consumer, "consumer");

      var builder = NoiseTransform.builder().copyFrom(noises.get(noiseKey));
      consumer.accept(builder);
      var transform = builder.build();
      if (transform.isEmpty()) {
        noises.remove(noiseKey);
      } else {
        noises.put(noiseKey, transform);
      }
      return this;
    }

    public Builder densityFunction(
        String densityFunctionKey, Consumer<NoiseTransform.Builder> consumer) {
      Objects.requireNonNull(densityFunctionKey, "densityFunctionKey");
      Objects.requireNonNull(consumer, "consumer");

      var builder = NoiseTransform.builder().copyFrom(densityFunctions.get(densityFunctionKey));
      consumer.accept(builder);
      var transform = builder.build();
      if (transform.isEmpty()) {
        densityFunctions.remove(densityFunctionKey);
      } else {
        densityFunctions.put(densityFunctionKey, transform);
      }
      return this;
    }

    public Builder copyFrom(NoiseConstraints constraints) {
      if (constraints == null) return this;
      clearParent = constraints.clearsParent();
      noises.clear();
      densityFunctions.clear();
      noises.putAll(constraints.noises());
      densityFunctions.putAll(constraints.densityFunctions());
      return this;
    }

    public NoiseConstraints build() {
      var builtNoises = new LinkedHashMap<String, NoiseTransform>(noises);
      var builtDensity = new LinkedHashMap<String, NoiseTransform>(densityFunctions);
      return new NoiseConstraints(builtNoises, builtDensity, clearParent);
    }
  }
}
