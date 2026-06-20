package terrasect

import com.mojang.serialization.MapCodec
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.LootContext
import net.neoforged.neoforge.common.loot.IGlobalLootModifier
import net.neoforged.neoforge.common.loot.LootModifier
import terrasect.handler.LootHandler

class RegionLootModifier : LootModifier(emptyArray()) {
  companion object {
    @JvmField val CODEC: MapCodec<RegionLootModifier> = MapCodec.unit(::RegionLootModifier)
  }

  override fun doApply(
    generatedLoot: ObjectArrayList<ItemStack>,
    context: LootContext,
  ): ObjectArrayList<ItemStack> {
    LootHandler.filterDrops(context, generatedLoot)
    return generatedLoot
  }

  override fun codec(): MapCodec<out IGlobalLootModifier> = CODEC
}
