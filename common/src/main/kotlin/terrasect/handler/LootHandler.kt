package terrasect.handler

import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import terrasect.compat.ResourceKeyCompat
import terrasect.extender.ChunkAccessExtender
import terrasect.generation.DimensionContext
import terrasect.instrumentation.TerrasectInstr
import terrasect.instrumentation.TerrasectMetricEvent

private val instr = TerrasectInstr.loot

object LootHandler {
  @JvmStatic
  fun filterDrops(context: LootContext, drops: MutableList<ItemStack>) {
    val origin = context.getOptionalParameter(LootContextParams.ORIGIN) ?: return
    val level = context.level
    val dimId = ResourceKeyCompat.getKeyId(level.dimension())
    val ctx = DimensionContext.get(dimId) ?: return
    val lookup = ctx.lootLookup ?: return
    val blockX = origin.x.toInt()
    val blockZ = origin.z.toInt()
    val chunk = level.getChunk(blockX shr 4, blockZ shr 4)
    val region =
      (chunk as? ChunkAccessExtender)?.`terrasect$getContext`()?.getRegion(blockX, blockZ)
        ?: ctx.traverser.traverse(blockX, blockZ).region
    if (region.loot == null) return
    instr.count(TerrasectMetricEvent.LOOT_APPLIED)
    drops.removeIf { !lookup.allowDrop(region, it.item) }
  }
}
