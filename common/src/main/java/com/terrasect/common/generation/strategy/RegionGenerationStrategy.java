package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.Region;

import java.util.List;

public interface RegionGenerationStrategy {
    void traverse(List<Region> children, TraversalScratch scratch);
}
