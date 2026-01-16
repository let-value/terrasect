package com.terrasect.common;

import com.terrasect.common.generation.World;

public interface Context {

  long getSeed();

  long getInfluence(int x, int z);

  default String getDimensionId() {
    return World.OVERWORLD;
  }
}
