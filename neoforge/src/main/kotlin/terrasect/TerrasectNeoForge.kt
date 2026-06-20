package terrasect

import com.mojang.logging.LogUtils
import com.mojang.serialization.MapCodec
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.loot.IGlobalLootModifier
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries

@Mod(Constants.MOD_ID)
class TerrasectNeoForge(modEventBus: IEventBus, modContainer: ModContainer) {

  companion object {
    private val log = LogUtils.getLogger()

    private val lootModifierSerializers: DeferredRegister<MapCodec<out IGlobalLootModifier>> =
      DeferredRegister.create(
        NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
        Constants.MOD_ID,
      )

    @Suppress("unused")
    private val regionLootFilterType =
      lootModifierSerializers.register(
        "region_loot_filter",
        java.util.function.Supplier { RegionLootModifier.CODEC },
      )
  }

  init {
    lootModifierSerializers.register(modEventBus)
    modEventBus.addListener(::commonSetup)
    modEventBus.addListener(::clientSetup)

    NeoForge.EVENT_BUS.register(this)

    log.info("Hello from ${Constants.MOD_NAME} on NeoForge!")
  }

  private fun commonSetup(event: FMLCommonSetupEvent) {
    log.info("${Constants.MOD_NAME} common setup")

    Terrasect.init()
  }

  private fun clientSetup(event: FMLClientSetupEvent) {
    log.info("${Constants.MOD_NAME} client setup")
    log.info("MINECRAFT NAME >> {}", Minecraft.getInstance().user.name)
  }

  @SubscribeEvent
  fun onServerStarting(event: ServerStartingEvent) {
    log.info("${Constants.MOD_NAME} server starting")
  }
}
