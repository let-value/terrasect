package com.terrasect.common.gui;

import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.generation.TraversalIterator;
import com.terrasect.common.generation.World;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugEntryCategory;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

public class RegionDebugEntry implements DebugScreenEntry {

  @Override
  public void display(
      DebugScreenDisplayer lines,
      @Nullable Level level,
      @Nullable LevelChunk clientChunk,
      @Nullable LevelChunk chunk) {
    var mc = Minecraft.getInstance();
    var cameraEntity = mc.getCameraEntity();
    if (cameraEntity == null) return;

    var context = MinecraftContext.get(level.dimension());
    if (context == null) return;

    var blockX = cameraEntity.getBlockX();
    var blockZ = cameraEntity.getBlockZ();

    var sb = new StringBuilder("Regions: ");

    TraversalIterator iter = World.traverseIterator(context, blockX, blockZ);
    if (iter == null) return;

    var depth = 0;
    do {
      var step = iter.current();
      var region = step.region;
      if (region == null) break;

      if (depth > 0) {
        sb.append(">");
      }
      sb.append(region.name());
      sb.append(
          String.format("(e:%.0f%%,i:%.0f%%)", step.edgeDistance * 100, step.edgeInfluence * 100));

      depth++;
    } while (iter.hasNext() && iter.next() != null && depth < 5);

    if (depth > 0) {
      lines.addLine(sb.toString());
    }
  }

  @Override
  public boolean isAllowed(boolean reducedDebugInfo) {
    return true;
  }

  @Override
  public DebugEntryCategory category() {
    return DebugEntryCategory.SCREEN_TEXT;
  }
}
