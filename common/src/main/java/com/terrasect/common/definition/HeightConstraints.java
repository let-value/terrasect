package com.terrasect.common.definition;

public record HeightConstraints(Integer minY, Integer maxY, boolean clearsParent) {

  public HeightConstraints {
    var hasLower = minY != null;
    var hasUpper = maxY != null;

    if (hasLower != hasUpper) {
      throw new IllegalArgumentException(
          "HeightConstraints requires both minY and maxY or neither");
    }

    if (clearsParent && hasLower) {
      throw new IllegalArgumentException("HeightConstraints cannot clear parent and set bounds");
    }

    if (hasLower && minY > maxY) {
      var tmp = minY;
      minY = maxY;
      maxY = tmp;
    }
  }

  public static HeightConstraints empty() {
    return new HeightConstraints(null, null, false);
  }

  public boolean hasConstraints() {
    return minY != null;
  }

  public HeightConstraints resolveWithParent(HeightConstraints parent) {
    if (!hasConstraints() && !clearsParent && parent != null) {
      return parent;
    }
    return this;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private Integer minY;
    private Integer maxY;
    private boolean clearsParent;

    public Builder copyFrom(HeightConstraints constraints) {
      if (constraints == null) {
        return this;
      }
      minY = constraints.minY();
      maxY = constraints.maxY();
      clearsParent = constraints.clearsParent();
      return this;
    }

    public Builder inherit() {
      minY = null;
      maxY = null;
      clearsParent = false;
      return this;
    }

    public Builder unconstrained() {
      minY = null;
      maxY = null;
      clearsParent = true;
      return this;
    }

    public Builder range(int minY, int maxY) {
      this.minY = minY;
      this.maxY = maxY;
      clearsParent = false;
      return this;
    }

    public Builder exact(int y) {
      minY = y;
      maxY = y;
      clearsParent = false;
      return this;
    }

    public HeightConstraints build() {
      return new HeightConstraints(minY, maxY, clearsParent);
    }
  }
}
