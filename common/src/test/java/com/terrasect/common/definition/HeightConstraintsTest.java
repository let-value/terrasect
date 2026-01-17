package com.terrasect.common.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class HeightConstraintsTest {

  @Test
  void builderCopiesAndBuildsConstraints() {
    var original = HeightConstraints.builder().range(40, 60).build();

    var built = HeightConstraints.builder().copyFrom(original).build();

    assertEquals(original.minY(), built.minY());
    assertEquals(original.maxY(), built.maxY());
    assertTrue(built.hasConstraints());
  }

  @Test
  void builderNormalizesRangeOrdering() {
    var constraints = HeightConstraints.builder().range(90, 20).build();

    assertEquals(20, constraints.minY());
    assertEquals(90, constraints.maxY());
  }

  @Test
  void regionBuilderConfiguresHeightThroughConsumer() {
    var definition =
        RegionDefinition.builder()
            .height(builder -> builder.exact(80))
            .height(builder -> builder.range(30, 50))
            .build();

    assertEquals(30, definition.height().minY());
    assertEquals(50, definition.height().maxY());
  }
}
