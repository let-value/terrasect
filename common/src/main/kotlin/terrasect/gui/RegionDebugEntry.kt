package terrasect.gui

//? if >=1.21.11 {
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.debug.DebugEntryCategory
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer
import net.minecraft.client.gui.components.debug.DebugScreenEntry
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk
import terrasect.compat.ResourceKeyCompat
import terrasect.generation.DimensionContext

//?}

//? if >=1.21.11 {
class RegionDebugEntry : DebugScreenEntry {
  override fun display(
    lines: DebugScreenDisplayer,
    level: Level?,
    clientChunk: LevelChunk?,
    chunk: LevelChunk?,
  ) {
    val dimension = level?.dimension() ?: return
    val mc = Minecraft.getInstance()
    val cameraEntity = mc.cameraEntity ?: return

    val context = DimensionContext.get(ResourceKeyCompat.getKeyId(dimension)) ?: return

    val blockX = cameraEntity.blockX
    val blockZ = cameraEntity.blockZ

    val sb = StringBuilder("Regions: ")

    val step = context.traverser.iterate(blockX, blockZ, context.cache)

    var depth = 0
    do {
      if (depth > 0) {
        sb.append(">")
      }
      depth++
      sb.append(step.region.name)
      if (step.distance == Float.NEGATIVE_INFINITY) {
        continue
      }
      sb.append(step.distance)
    } while (step.next() !== null && depth < 5)

    lines.addLine(sb.toString())
  }

  override fun isAllowed(reducedDebugInfo: Boolean): Boolean {
    return true
  }

  override fun category(): DebugEntryCategory {
    return DebugEntryCategory.SCREEN_TEXT
  }
}
//?} else
/*class RegionDebugEntry*/
