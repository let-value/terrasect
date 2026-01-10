package com.terrasect.common.generation;

import com.terrasect.common.definition.Region;

/**
 * Zero-allocation iterator for stepping through region hierarchy one depth at a time.
 *
 * <p>Thread-local instance is reused. Caller must consume {@link #current()} values
 * before calling {@link #next()} or starting a new traversal on the same thread.
 *
 * <p>Usage:
 * <pre>{@code
 * TraversalIterator iter = World.traverseIterator(context, x, z);
 * if (iter != null) {
 *     do {
 *         TraversalResult step = iter.current();
 *         // use step.region, step.seed, step.edgeDistance, step.edgeInfluence
 *     } while (iter.hasNext() && iter.next() != null);
 * }
 * }</pre>
 */
public final class TraversalIterator {

    // The result that gets mutated each step
    final TraversalResult result = new TraversalResult();

    // Current region for hasNext() check - Layout updates this
    Region currentRegion;

    TraversalIterator() {}

    /**
     * Get the current traversal result.
     *
     * <p>Valid after iterator is started and before it's reused.
     * The result is mutated in place on each {@link #next()} call.
     */
    public TraversalResult current() {
        return result;
    }

    /**
     * Check if more depth levels are available.
     */
    public boolean hasNext() {
        return currentRegion.hasChildren();
    }

    /**
     * Advance to the next depth level.
     *
     * <p>Mutates {@link #current()} with the new region's data.
     *
     * @return this iterator for chaining, or null if no more levels
     */
    public TraversalIterator next() {
        return Layout.step(this);
    }

    /**
     * Advance to the deepest level (leaf region).
     *
     * <p>Convenience method equivalent to calling {@link #next()} until exhausted.
     *
     * @return this iterator with result pointing to leaf
     */
    public TraversalIterator toLeaf() {
        while (hasNext()) {
            next();
        }
        return this;
    }
}
