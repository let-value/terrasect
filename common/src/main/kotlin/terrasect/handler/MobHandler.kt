package terrasect.handler

import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.chunk.ChunkAccess
import terrasect.extender.ChunkAccessExtender
import terrasect.instrumentation.TerrasectInstr
import terrasect.instrumentation.TerrasectMetricEvent

private val instr = TerrasectInstr.mob

object MobHandler {
  @JvmStatic
  fun allowSpawn(
    chunkAccess: ChunkAccess,
    blockX: Int,
    blockZ: Int,
    entityType: EntityType<*>,
  ): Boolean {
    val chunkContext = (chunkAccess as ChunkAccessExtender).`terrasect$getContext`() ?: return true
    val ctx = chunkContext.dimensionContext ?: return true
    val lookup = ctx.mobLookup ?: return true
    val region = chunkContext.getRegion(blockX, blockZ) ?: return true
    instr.count(TerrasectMetricEvent.MOB_APPLIED)
    return lookup.allowSpawn(region, entityType)
  }
}
