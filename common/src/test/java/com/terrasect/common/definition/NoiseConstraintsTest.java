package com.terrasect.common.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class NoiseConstraintsTest {

  @Test
  public void mergesParentAndChildNoiseConstraints() {
    var parent =
        RegionDefinition.builder()
            .noise(n -> n.noise("minecraft:continentalness", t -> t.clamp(0.1, 2.0)))
            .build();

    var child =
        RegionDefinition.builder()
            .noise(n -> n.densityFunction("minecraft:overworld/continents", t -> t.clamp(0.0, 1.0)))
            .build()
            .resolveInherited(parent);

    var resolved = child.noise();
    assertTrue(resolved.noises().containsKey("minecraft:continentalness"));
    assertTrue(resolved.densityFunctions().containsKey("minecraft:overworld/continents"));
    assertTrue(resolved.hasAnyConstraints());
  }

  @Test
  public void childCanOverrideParentKey() {
    var parent =
        RegionDefinition.builder()
            .noise(n -> n.noise("minecraft:continentalness", t -> t.clamp(-1.0, -0.2)))
            .build();

    var child =
        RegionDefinition.builder()
            .noise(n -> n.noise("minecraft:continentalness", t -> t.clamp(0.2, 1.0)))
            .build()
            .resolveInherited(parent);

    var transform = child.noise().noises().get("minecraft:continentalness");
    assertEquals(1, transform.operations().size());
    assertEquals(new NoiseTransform.Clamp(0.2, 1.0), transform.operations().getFirst());
  }

  @Test
  public void unconstrainedClearsParentConstraints() {
    var parent =
        RegionDefinition.builder()
            .noise(n -> n.noise("minecraft:continentalness", t -> t.clamp(0.1, 2.0)))
            .build();

    var child =
        RegionDefinition.builder()
            .noise(noise -> noise.clearParent())
            .build()
            .resolveInherited(parent);

    var resolved = child.noise();
    assertFalse(resolved.hasAnyConstraints());
    assertTrue(resolved.noises().isEmpty());
    assertTrue(resolved.densityFunctions().isEmpty());
  }
}
