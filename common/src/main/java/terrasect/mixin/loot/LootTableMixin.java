package terrasect.mixin.loot;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.handler.LootHandler;

@Mixin(LootTable.class)
public class LootTableMixin {
  @Inject(
      method =
          "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
      at = @At("RETURN"))
  private void terrasect$filterRegionLoot(
      LootContext context, CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir) {
    LootHandler.filterDrops(context, cir.getReturnValue());
  }
}
