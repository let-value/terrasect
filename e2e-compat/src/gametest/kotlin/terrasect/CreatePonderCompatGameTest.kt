package terrasect

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("CreatePonderCompatGameTest")

private val ponderItems =
  listOf(
    "create:cogwheel",
    "create:mechanical_press",
    "create:encased_fan",
  )

private fun setScreen(client: Minecraft, screen: Screen?) {
  client.javaClass.methods
    .first { method ->
      method.parameterCount == 1 && Screen::class.java.isAssignableFrom(method.parameterTypes[0])
    }
    .invoke(client, screen)
}

private fun currentScreen(client: Minecraft): Screen? {
  val field =
    client.javaClass.declaredFields.first { field ->
      Screen::class.java.isAssignableFrom(field.type)
    }
  field.isAccessible = true
  return field.get(client) as Screen?
}

@Suppress("UnstableApiUsage")
object CreatePonderCompatGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    assertTrue(
      FabricLoader.getInstance().isModLoaded("create"),
      "Create Fly must be loaded for this compatibility test",
    )

    val game = context.worldBuilder().setUseConsistentSettings(false).create()
    try {
      context.waitTicks(20)
      context.runOnClient(
        FailableConsumer<Minecraft, Exception> { client ->
          val level =
            checkNotNull(client.level) {
              "Create Ponder compatibility test requires an active client level"
            }
          val sourceClass =
            Class.forName("com.zurrtum.create.catnip.levelWrappers.SchematicChunkSource")
          val source = sourceClass.getConstructor(Level::class.java).newInstance(level)
          sourceClass
            .getMethod(
              "getChunkForLighting",
              Int::class.javaPrimitiveType,
              Int::class.javaPrimitiveType,
            )
            .invoke(source, 0, 0)

          val ponderUiClass =
            Class.forName("com.zurrtum.create.client.ponder.foundation.ui.PonderUI")
          for (itemId in ponderItems) {
            val item =
              BuiltInRegistries.ITEM.get(Identifier.parse(itemId)).orElseThrow {
                IllegalStateException("Create item must be registered: $itemId")
              }
            val screen =
              ponderUiClass.getMethod("of", ItemStack::class.java).invoke(null, ItemStack(item))
                as Screen
            setScreen(client, screen)
            log.info("Opened Create Ponder screen for {}", itemId)
          }
        }
      )

      context.waitTicks(20)
      context.runOnClient(
        FailableConsumer<Minecraft, Exception> { client ->
          val screen = currentScreen(client)
          assertTrue(
            screen?.javaClass?.name == "com.zurrtum.create.client.ponder.foundation.ui.PonderUI",
            "Create Ponder screen was not opened: ${screen?.javaClass?.name}",
          )
          val activeScene = screen!!.javaClass.getMethod("getActiveScene").invoke(screen)
          assertTrue(activeScene != null, "Create Ponder screen did not initialize an active scene")
          setScreen(client, null)
        }
      )
    } finally {
      game.close()
    }

    log.info("Create Ponder compatibility test passed: synthetic chunk created without a crash")
  }
}
