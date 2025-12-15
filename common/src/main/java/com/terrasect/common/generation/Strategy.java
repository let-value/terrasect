package com.terrasect.common.generation;

public interface Strategy {
    long getSeed();
    float getRiverInfluence(int x, int z);
    float getRidgeInfluence(int x, int z);
}
