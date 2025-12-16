package com.terrasect.common.generation;

import com.terrasect.common.generation.definition.RegionDefinition;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public record Region(
    String name,
    int areaBudget,
    RegionDefinition definition,
    Set<String> adjacentTo,
    List<Region> children
) {
    public Region {
        if (definition == null) definition = RegionDefinition.empty();
        if (adjacentTo == null) adjacentTo = Collections.emptySet();
        if (children == null) children = Collections.emptyList();
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }
}
