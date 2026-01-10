package com.terrasect.common.definition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public record NoiseConstraints(Map<String, NoiseTransform> noises, Map<String, NoiseTransform> densityFunctions) {
    private static final String CLEAR_PARENT_MARKER = "\u0000terrasect:clear_parent\u0000";
    private static final NoiseTransform MARKER_VALUE = NoiseTransform.empty();

    private static final NoiseConstraints EMPTY = new NoiseConstraints(Collections.emptyMap(), Collections.emptyMap());

    public NoiseConstraints {
        if (noises == null) noises = Collections.emptyMap();
        if (densityFunctions == null) densityFunctions = Collections.emptyMap();
        noises = Map.copyOf(noises);
        densityFunctions = Map.copyOf(densityFunctions);
    }

    public static NoiseConstraints empty() {
        return EMPTY;
    }

    public static NoiseConstraints clearParent() {
        return new NoiseConstraints(Map.of(CLEAR_PARENT_MARKER, MARKER_VALUE), Collections.emptyMap());
    }

    public boolean hasAnyConstraints() {
        for (String key : noises.keySet()) {
            if (!CLEAR_PARENT_MARKER.equals(key)) return true;
        }
        for (String key : densityFunctions.keySet()) {
            if (!CLEAR_PARENT_MARKER.equals(key)) return true;
        }
        return false;
    }

    public NoiseConstraints resolveWithParent(NoiseConstraints parent) {
        boolean clearParent = clearsParent();

        boolean hasLocal = hasAnyConstraints();
        if (!hasLocal) {
            if (clearParent) {
                return NoiseConstraints.empty();
            }
            return Objects.requireNonNullElse(parent, NoiseConstraints.empty());
        }

        NoiseConstraints base =
                clearParent ? NoiseConstraints.empty() : Objects.requireNonNullElse(parent, NoiseConstraints.empty());
        if (base.noises.isEmpty() && base.densityFunctions.isEmpty() && !clearParent) {
            return this;
        }

        Map<String, NoiseTransform> mergedNoises =
                base.noises.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(base.noises);
        for (var entry : noises.entrySet()) {
            if (CLEAR_PARENT_MARKER.equals(entry.getKey())) continue;
            mergedNoises.put(entry.getKey(), entry.getValue());
        }

        Map<String, NoiseTransform> mergedDensity =
                base.densityFunctions.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(base.densityFunctions);
        for (var entry : densityFunctions.entrySet()) {
            if (CLEAR_PARENT_MARKER.equals(entry.getKey())) continue;
            mergedDensity.put(entry.getKey(), entry.getValue());
        }

        if (mergedNoises.isEmpty() && mergedDensity.isEmpty()) {
            return NoiseConstraints.empty();
        }

        return new NoiseConstraints(mergedNoises, mergedDensity);
    }

    private boolean clearsParent() {
        return noises.containsKey(CLEAR_PARENT_MARKER) || densityFunctions.containsKey(CLEAR_PARENT_MARKER);
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
            noises.clear();
            densityFunctions.clear();
            return this;
        }

        public Builder noConstraints() {
            clearParent = true;
            noises.clear();
            densityFunctions.clear();
            return this;
        }

        public Builder noise(String noiseKey, Consumer<NoiseTransform.Builder> consumer) {
            Objects.requireNonNull(noiseKey, "noiseKey");
            Objects.requireNonNull(consumer, "consumer");

            NoiseTransform.Builder builder = NoiseTransform.builder().copyFrom(noises.get(noiseKey));
            consumer.accept(builder);
            NoiseTransform transform = builder.build();
            if (transform.isEmpty()) {
                noises.remove(noiseKey);
            } else {
                noises.put(noiseKey, transform);
            }
            return this;
        }

        public Builder densityFunction(String densityFunctionKey, Consumer<NoiseTransform.Builder> consumer) {
            Objects.requireNonNull(densityFunctionKey, "densityFunctionKey");
            Objects.requireNonNull(consumer, "consumer");

            NoiseTransform.Builder builder =
                    NoiseTransform.builder().copyFrom(densityFunctions.get(densityFunctionKey));
            consumer.accept(builder);
            NoiseTransform transform = builder.build();
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
            noises.remove(CLEAR_PARENT_MARKER);
            densityFunctions.remove(CLEAR_PARENT_MARKER);
            return this;
        }

        public NoiseConstraints build() {
            if (!clearParent && noises.isEmpty() && densityFunctions.isEmpty()) {
                return NoiseConstraints.empty();
            }

            Map<String, NoiseTransform> builtNoises = new LinkedHashMap<>(noises);
            Map<String, NoiseTransform> builtDensity = new LinkedHashMap<>(densityFunctions);
            if (clearParent) {
                builtNoises.put(CLEAR_PARENT_MARKER, MARKER_VALUE);
            }
            return new NoiseConstraints(builtNoises, builtDensity);
        }
    }
}
