package com.terrasect.common.api;

import com.terrasect.common.generation.definition.RegionDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public record Region(
    String name,
    int areaBudget,
    RegionDefinition definition,
    Set<String> adjacentTo,
    List<Region> children,
    List<String> sortedAdjacentTo
) {

    public Region {
        if (definition == null) definition = RegionDefinition.empty();
        if (adjacentTo == null) adjacentTo = Collections.emptySet();
        if (children == null) children = Collections.emptyList();

        List<String> sortedAdjacency = new ArrayList<>(adjacentTo);
        Collections.sort(sortedAdjacency);
        sortedAdjacentTo = Collections.unmodifiableList(sortedAdjacency);
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public List<String> sortedAdjacentTo() {
        return sortedAdjacentTo;
    }
}
