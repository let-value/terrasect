package terrasect

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper

// Old-paradigm registration (pre-1.21.2 gametest framework): implement FabricGameTest and annotate
// with the vanilla @GameTest. Used on versions without the new GameTestInstance registry (1.20.1,
// 1.21.1) — exactly the versions that have no client-gametest API. The assertion body is shared
// via ServerSmokeGuard; the preset is forced at mod init by ServerSmokeInit.
class ServerSmokeGameTest : FabricGameTest {
  @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
  fun pipeline(helper: GameTestHelper) {
    helper.succeedWhen { ServerSmokeGuard.assertPipeline(helper.level) }
  }
}
