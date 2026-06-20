package terrasect

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import org.slf4j.LoggerFactory
import terrasect.handler.LootHandler

object TerrasectFabric : ModInitializer {
  private val log = LoggerFactory.getLogger(Constants.MOD_ID)

  override fun onInitialize() {
    log.info("Hello from ${Constants.MOD_NAME} on Fabric!")

    Terrasect.init()

    @Suppress("UNCHECKED_CAST")
    LootTableEvents.MODIFY_DROPS.register { _, context, drops ->
      LootHandler.filterDrops(context, drops as MutableList)
    }
  }
}
