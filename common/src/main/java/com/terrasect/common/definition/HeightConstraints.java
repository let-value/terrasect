package com.terrasect.common.definition;

/**
 * Optional terrain height constraints for a region.
 *
 * <p>When set, terrain surface heights in the region are mapped into the configured Y-range.
 * This is consumed by {@code TerrainHeightLookup} to clamp generation above the computed surface.
 *
 * <p>Constraints are inherited by default. Use {@link #unconstrained()} to explicitly clear any
 * parent constraints.
 */
public record HeightConstraints(int minY, int maxY) {
    private static final int INHERIT_SENTINEL = Integer.MIN_VALUE;
    private static final int UNCONSTRAINED_SENTINEL = Integer.MIN_VALUE + 1;

    private static final HeightConstraints INHERIT = new HeightConstraints(INHERIT_SENTINEL, INHERIT_SENTINEL);
    private static final HeightConstraints UNCONSTRAINED = new HeightConstraints(UNCONSTRAINED_SENTINEL, UNCONSTRAINED_SENTINEL);

    public HeightConstraints {
        boolean inherit = minY == INHERIT_SENTINEL || maxY == INHERIT_SENTINEL;
        boolean unconstrained = minY == UNCONSTRAINED_SENTINEL || maxY == UNCONSTRAINED_SENTINEL;

        if (inherit && unconstrained) {
            throw new IllegalArgumentException("HeightConstraints cannot be both inherit and unconstrained");
        }

        if (inherit) {
            if (minY != INHERIT_SENTINEL || maxY != INHERIT_SENTINEL) {
                throw new IllegalArgumentException("HeightConstraints inherit must set both minY and maxY");
            }
        } else if (unconstrained) {
            if (minY != UNCONSTRAINED_SENTINEL || maxY != UNCONSTRAINED_SENTINEL) {
                throw new IllegalArgumentException("HeightConstraints unconstrained must set both minY and maxY");
            }
        } else if (minY > maxY) {
            int tmp = minY;
            minY = maxY;
            maxY = tmp;
        }
    }

    public static HeightConstraints inherit() {
        return INHERIT;
    }

    public static HeightConstraints unconstrained() {
        return UNCONSTRAINED;
    }

    public static HeightConstraints range(int minY, int maxY) {
        return new HeightConstraints(minY, maxY);
    }

    public static HeightConstraints exact(int y) {
        return new HeightConstraints(y, y);
    }

    public boolean isInherit() {
        return minY == INHERIT_SENTINEL;
    }

    public boolean isUnconstrained() {
        return minY == UNCONSTRAINED_SENTINEL;
    }

    public boolean hasConstraints() {
        return !isInherit() && !isUnconstrained();
    }

    public HeightConstraints resolveWithParent(HeightConstraints parent) {
        if (isInherit() && parent != null) {
            return parent;
        }
        return this;
    }
}
