package com.terrasect.common.generation;

import com.terrasect.common.definition.Region;

public final class TraversalIterator {

    final TraversalResult result = new TraversalResult();

    Region currentRegion;

    TraversalIterator() {}

    public TraversalResult current() {
        return result;
    }

    public boolean hasNext() {
        return currentRegion.hasChildren();
    }

    public TraversalIterator next() {
        return Layout.step(this);
    }

    public TraversalIterator toLeaf() {
        while (hasNext()) {
            next();
        }
        return this;
    }
}
