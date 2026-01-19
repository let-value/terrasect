package com.terrasect.common.strategy;

import com.terrasect.common.definition.GenerationStrategy;
import com.terrasect.common.definition.Region;
import com.terrasect.common.util.MathUtils;
import java.util.List;

public final class HexStrategy {

  private HexStrategy() {}

  public static void query(
      long seed,
      List<Region> children,
      float dx,
      float dz,
      float radius,
      GenerationStrategy.Hex strategy,
      QueryResult out) {
    var ringIndex = findChildByName(children, strategy.ringRegionName());
    var ringWidth = ringIndex >= 0 ? getRingWidth(children.get(ringIndex)) : 0F;

    var gridRadius = radius * (1.0f + ringWidth);

    var packedHex = MathUtils.getHexCell(dx, dz, gridRadius);
    var q = (int) (packedHex >> 32);
    var r = (int) packedHex;

    out.siteX = q;
    out.siteZ = r;

    var hexCenterWorldX = ((float) Math.sqrt(3) * q + (float) Math.sqrt(3) / 2.0f * r) * gridRadius;
    var hexCenterWorldZ = (3.0f / 2.0f * r) * gridRadius;

    var offsetX = dx - hexCenterWorldX;
    var offsetZ = dz - hexCenterWorldZ;
    var distFromCenter = (float) Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);

    if (ringIndex >= 0 && distFromCenter > radius) {

      out.childIndex = ringIndex;

      out.centerX = hexCenterWorldX / radius;
      out.centerZ = hexCenterWorldZ / radius;
      out.radius = ringWidth;
      var ringDist = distFromCenter - radius;
      out.edgeDistance = 1.0f - Math.min(1.0f, ringDist / (ringWidth * radius));
      return;
    }

    var hexDist = (Math.abs(q) + Math.abs(q + r) + Math.abs(r)) / 2;

    int childIndex;
    if (hexDist == 0) {
      childIndex = pickCenterChild(children, ringIndex);
    } else {
      childIndex = pickChildWeighted(children, ringIndex, MathUtils.hash64(seed, q, r, 9999));
    }

    out.childIndex = childIndex;

    out.centerX = hexCenterWorldX / radius;
    out.centerZ = hexCenterWorldZ / radius;

    out.radius = 1.0f;

    out.edgeDistance = 1.0f - Math.min(1.0f, distFromCenter / radius);
  }

  public static long getSeed(long parentSeed, int q, int r) {
    return MathUtils.hash64(parentSeed, q, r, 9999);
  }

  private static int findChildByName(List<Region> children, String name) {
    if (name == null) {
      return -1;
    }
    for (var i = 0; i < children.size(); i++) {
      if (children.get(i).name().equals(name)) {
        return i;
      }
    }
    return -1;
  }

  private static float getRingWidth(Region ringRegion) {
    var ringWidth = ringRegion.areaBudget() / 100.0f;
    return Math.max(0.05f, Math.min(0.5f, ringWidth));
  }

  private static int pickChildWeighted(List<Region> children, int excludeIndex, long seed) {
    var randomVal = (MathUtils.hash64(seed, 0, 0, 0) & 0xFFFF) / 65536.0f;
    var totalWeight = 0F;
    for (var i = 0; i < children.size(); i++) {
      if (i == excludeIndex) {
        continue;
      }
      totalWeight += children.get(i).areaBudget();
    }
    if (totalWeight <= 0F) {
      return pickCenterChild(children, excludeIndex);
    }
    var target = randomVal * totalWeight;
    var currentW = 0F;
    for (var i = 0; i < children.size(); i++) {
      if (i == excludeIndex) {
        continue;
      }
      currentW += children.get(i).areaBudget();
      if (currentW >= target) return i;
    }
    return pickCenterChild(children, excludeIndex);
  }

  private static int pickCenterChild(List<Region> children, int excludeIndex) {
    if (children.isEmpty()) {
      return 0;
    }
    if (excludeIndex < 0 || children.size() == 1) {
      return 0;
    }
    return excludeIndex == 0 ? 1 : 0;
  }
}
