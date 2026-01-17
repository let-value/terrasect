package com.terrasect.common.strategy;

import com.terrasect.common.definition.GenerationStrategy;
import com.terrasect.common.definition.Region;
import com.terrasect.common.util.MathUtils;
import java.util.List;

public final class SubdivisionStrategy {

  private static final ThreadLocal<float[]> BUDGETS_BUFFER =
      ThreadLocal.withInitial(() -> new float[8]);
  private static final ThreadLocal<int[]> INDICES_BUFFER =
      ThreadLocal.withInitial(() -> new int[8]);

  private SubdivisionStrategy() {}

  public static void query(
      long seed,
      List<Region> children,
      float dx,
      float dz,
      float radius,
      GenerationStrategy.Subdivision strategy,
      QueryResult out) {
    if (children.isEmpty()) {
      out.childIndex = 0;
      out.centerX = 0;
      out.centerZ = 0;
      out.radius = 0.5f;
      return;
    }

    var count = children.size();
    if (count == 1) {
      out.childIndex = 0;
      out.centerX = 0;
      out.centerZ = 0;
      out.radius = 1.0f;
      return;
    }

    var jitter = strategy.jitter();

    var nx = dx / radius;
    var nz = dz / radius;

    var totalBudget = 0F;
    for (var i = 0; i < count; i++) {
      totalBudget += children.get(i).areaBudget();
    }

    float[] budgets = getBudgetsBuffer(count);
    for (var i = 0; i < count; i++) {
      budgets[i] = children.get(i).areaBudget() / totalBudget;
    }

    int[] indices = getIndicesBuffer(count);
    sortByBudgetDescending(budgets, indices, count);

    traverseBSP(nx, nz, budgets, indices, 0, count, -1, -1, 1, 1, seed, 0, jitter, out);
  }

  private static void traverseBSP(
      float px,
      float pz,
      float[] budgets,
      int[] indices,
      int start,
      int end,
      float minX,
      float minZ,
      float maxX,
      float maxZ,
      long seed,
      int depth,
      float jitterAmount,
      QueryResult out) {
    var count = end - start;

    if (count == 1) {

      var originalIdx = indices[start];
      out.childIndex = originalIdx;

      var centerX = (minX + maxX) / 2.0f;
      var centerZ = (minZ + maxZ) / 2.0f;
      var halfWidth = (maxX - minX) / 2.0f;
      var halfHeight = (maxZ - minZ) / 2.0f;

      var cellRadius = Math.min(halfWidth, halfHeight);
      cellRadius = Math.max(cellRadius, 0.1f);

      out.centerX = centerX;
      out.centerZ = centerZ;
      out.radius = cellRadius;

      out.siteX = centerX;
      out.siteZ = centerZ;

      var distToLeft = px - minX;
      var distToRight = maxX - px;
      var distToBottom = pz - minZ;
      var distToTop = maxZ - pz;
      var minDist = Math.min(Math.min(distToLeft, distToRight), Math.min(distToBottom, distToTop));

      out.edgeDistance = Math.min(1.0f, minDist / cellRadius);
      return;
    }

    var totalBudget = 0F;
    for (var i = start; i < end; i++) {
      totalBudget += budgets[indices[i]];
    }

    var accumulated = 0F;
    var bestMid = start + 1;
    var bestDiff = Float.MAX_VALUE;

    for (var i = start; i < end - 1; i++) {
      accumulated += budgets[indices[i]];
      var diff = Math.abs(accumulated - totalBudget / 2);
      if (diff < bestDiff) {
        bestDiff = diff;
        bestMid = i + 1;
      }
    }

    var mid = bestMid;

    var leftBudget = 0F;
    for (var i = start; i < mid; i++) {
      leftBudget += budgets[indices[i]];
    }
    var splitRatio = leftBudget / totalBudget;

    var jitterVal = hashToFloat(seed, depth, 0);
    splitRatio = clamp(splitRatio + (jitterVal - 0.5f) * jitterAmount, 0.15f, 0.85f);

    var width = maxX - minX;
    var height = maxZ - minZ;
    var splitVertical = (width > height) ^ (hashToFloat(seed, depth, 1) > 0.7f);

    if (splitVertical) {
      var splitX = minX + width * splitRatio;

      var leftSeed = MathUtils.hash64(seed, depth, 0, 1);
      var rightSeed = MathUtils.hash64(seed, depth, 1, 1);

      if (px < splitX) {
        traverseBSP(
            px,
            pz,
            budgets,
            indices,
            start,
            mid,
            minX,
            minZ,
            splitX,
            maxZ,
            leftSeed,
            depth + 1,
            jitterAmount,
            out);
      } else {
        traverseBSP(
            px,
            pz,
            budgets,
            indices,
            mid,
            end,
            splitX,
            minZ,
            maxX,
            maxZ,
            rightSeed,
            depth + 1,
            jitterAmount,
            out);
      }
    } else {
      var splitZ = minZ + height * splitRatio;

      var leftSeed = MathUtils.hash64(seed, depth, 0, 1);
      var rightSeed = MathUtils.hash64(seed, depth, 1, 1);

      if (pz < splitZ) {
        traverseBSP(
            px,
            pz,
            budgets,
            indices,
            start,
            mid,
            minX,
            minZ,
            maxX,
            splitZ,
            leftSeed,
            depth + 1,
            jitterAmount,
            out);
      } else {
        traverseBSP(
            px,
            pz,
            budgets,
            indices,
            mid,
            end,
            minX,
            splitZ,
            maxX,
            maxZ,
            rightSeed,
            depth + 1,
            jitterAmount,
            out);
      }
    }
  }

  private static void sortByBudgetDescending(float[] budgets, int[] indices, int count) {
    for (var i = 0; i < count; i++) {
      indices[i] = i;
    }

    for (var i = 0; i < count - 1; i++) {
      for (var j = i + 1; j < count; j++) {
        if (budgets[indices[j]] > budgets[indices[i]]) {
          var tmp = indices[i];
          indices[i] = indices[j];
          indices[j] = tmp;
        }
      }
    }
  }

  public static long getSeed(long parentSeed, int childIndex, Region region) {
    return MathUtils.hash64(parentSeed, region.name().hashCode(), childIndex, 777);
  }

  private static float hashToFloat(long seed, int a, int b) {
    var h = MathUtils.hash64(seed, a, b, 0);
    return (h & 0xFFFF) / 65536.0f;
  }

  private static float clamp(float v, float min, float max) {
    return v < min ? min : (v > max ? max : v);
  }

  private static float[] getBudgetsBuffer(int count) {
    var buffer = BUDGETS_BUFFER.get();
    if (buffer.length < count) {
      buffer = new float[count];
      BUDGETS_BUFFER.set(buffer);
    }
    return buffer;
  }

  private static int[] getIndicesBuffer(int count) {
    var buffer = INDICES_BUFFER.get();
    if (buffer.length < count) {
      buffer = new int[count];
      INDICES_BUFFER.set(buffer);
    }
    return buffer;
  }
}
