package com.terrasect.common.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public record Region(
    String name,
    int areaBudget,
    float radius,
    int childrenTotalBudget,
    RegionDefinition definition,
    Set<String> adjacentTo,
    List<Region> children,
    List<String> sortedAdjacentTo,
    boolean anchoredToOrigin
) {

    public Region {
        if (definition == null) definition = RegionDefinition.empty();
        if (adjacentTo == null) adjacentTo = Collections.emptySet();
        if (children == null) children = Collections.emptyList();

        List<String> sortedAdjacency = new ArrayList<>(adjacentTo);
        Collections.sort(sortedAdjacency);
        sortedAdjacentTo = Collections.unmodifiableList(sortedAdjacency);
    }
    
    /**
     * Convenience constructor that computes radius and childrenTotalBudget.
     */
    public Region(String name, int areaBudget, RegionDefinition definition, 
                  Set<String> adjacentTo, List<Region> children, List<String> sortedAdjacentTo,
                  boolean anchoredToOrigin) {
        this(name, areaBudget, 
             (float) Math.sqrt(areaBudget),
             children.stream().mapToInt(Region::areaBudget).sum(),
             definition, adjacentTo, children, sortedAdjacentTo, anchoredToOrigin);
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public List<String> sortedAdjacentTo() {
        return sortedAdjacentTo;
    }
}
