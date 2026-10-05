package terrasect.gametest

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper

class ServerSmokeGameTest : FabricGameTest {
  @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
  fun pipeline(helper: GameTestHelper) {
    helper.succeedWhen { ServerSmokeGuard.assertPipeline(helper.level) }
  }
}
