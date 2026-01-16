package com.terrasect.common.mixin;

public interface ClimateTargetPointAccessor {
  void terrasect$setTemperature(long temperature);

  void terrasect$setHumidity(long humidity);

  void terrasect$setContinentalness(long continentalness);

  void terrasect$setErosion(long erosion);

  void terrasect$setDepth(long depth);

  void terrasect$setWeirdness(long weirdness);
}
