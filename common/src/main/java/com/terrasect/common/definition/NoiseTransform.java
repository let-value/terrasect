package com.terrasect.common.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record NoiseTransform(List<NoiseTransform.Operation> operations) {
  private static final NoiseTransform EMPTY = new NoiseTransform(Collections.emptyList());

  public NoiseTransform {
    if (operations == null) operations = Collections.emptyList();
    operations = List.copyOf(operations);
  }

  public static NoiseTransform empty() {
    return EMPTY;
  }

  public boolean isEmpty() {
    return operations.isEmpty();
  }

  public static Builder builder() {
    return new Builder();
  }

  public sealed interface Operation permits Clamp, Add, Multiply, Map {}

  public record Clamp(double min, double max) implements Operation {
    public Clamp {
      if (min > max) {
        var tmp = min;
        min = max;
        max = tmp;
      }
    }
  }

  public record Add(double value) implements Operation {}

  public record Multiply(double value) implements Operation {}

  public record Map(MapType type) implements Operation {
    public Map {
      if (type == null) throw new IllegalArgumentException("Map type cannot be null");
    }
  }

  public enum MapType {
    ABS,
    SQUARE,
    CUBE,
    HALF_NEGATIVE,
    QUARTER_NEGATIVE,
    INVERT,
    SQUEEZE
  }

  public static final class Builder {
    private final List<Operation> operations = new ArrayList<>();

    public Builder clamp(double min, double max) {
      operations.add(new Clamp(min, max));
      return this;
    }

    public Builder add(double value) {
      operations.add(new Add(value));
      return this;
    }

    public Builder multiply(double value) {
      operations.add(new Multiply(value));
      return this;
    }

    public Builder map(MapType type) {
      operations.add(new Map(type));
      return this;
    }

    public Builder abs() {
      return map(MapType.ABS);
    }

    public Builder square() {
      return map(MapType.SQUARE);
    }

    public Builder cube() {
      return map(MapType.CUBE);
    }

    public Builder halfNegative() {
      return map(MapType.HALF_NEGATIVE);
    }

    public Builder quarterNegative() {
      return map(MapType.QUARTER_NEGATIVE);
    }

    public Builder invert() {
      return map(MapType.INVERT);
    }

    public Builder squeeze() {
      return map(MapType.SQUEEZE);
    }

    public Builder copyFrom(NoiseTransform transform) {
      if (transform == null || transform.isEmpty()) return this;
      operations.addAll(transform.operations);
      return this;
    }

    public NoiseTransform build() {
      if (operations.isEmpty()) {
        return NoiseTransform.empty();
      }
      return new NoiseTransform(operations);
    }
  }
}
