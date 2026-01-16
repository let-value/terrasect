package com.terrasect.common.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.terrasect.common.util.MathUtils;
import org.junit.jupiter.api.Test;

public class MathUtilsTest {

  @Test
  public void testFloorDiv() {
    assertEquals(1, MathUtils.floorDiv(3, 2));
    assertEquals(-1, MathUtils.floorDiv(-1, 2));
    assertEquals(-2, MathUtils.floorDiv(-3, 2));
    assertEquals(0, MathUtils.floorDiv(1, 2));
    assertEquals(-1, MathUtils.floorDiv(-2, 2));
  }

  @Test
  public void testMod() {
    assertEquals(1, MathUtils.mod(1, 5));
    assertEquals(4, MathUtils.mod(-1, 5));
    assertEquals(0, MathUtils.mod(5, 5));
    assertEquals(0, MathUtils.mod(-5, 5));
  }

  @Test
  public void testHash64Determinism() {
    var h1 = MathUtils.hash64(123L, 10, 20, 30L);
    var h2 = MathUtils.hash64(123L, 10, 20, 30L);
    assertEquals(h1, h2);
  }
}
