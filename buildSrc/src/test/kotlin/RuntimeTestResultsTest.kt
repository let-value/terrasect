import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RuntimeTestResultsTest {
  @Test
  fun `rejects runner failures and child exit failures after assertion markers`() {
    val markers = mapOf("SmokeGameTest" to "smoke: OK")
    for (failure in
      listOf(
        "Client gametests failed with an exception",
        "Server smoke failed",
        "Minecraft exited with code: 1",
      )) {
      assertThrows(GradleException::class.java) {
        completedGameTests("smoke: OK\n$failure", markers, "SmokeGameTest")
      }
    }
  }

  @Test
  fun `reports only tests with completion markers and rejects zero executed tests`() {
    val markers =
      linkedMapOf(
        "SmokeGameTest" to "smoke: OK",
        "LootConstraintBlockAllGameTest" to "loot constraints: OK",
      )

    assertEquals(
      listOf("SmokeGameTest", "LootConstraintBlockAllGameTest"),
      completedGameTests(
        "smoke: OK\nloot constraints: OK",
        markers,
        "SmokeGameTest,LootConstraintBlockAllGameTest",
      ),
    )
    assertThrows(GradleException::class.java) {
      completedGameTests(
        "Minecraft started",
        markers,
        "SmokeGameTest,LootConstraintBlockAllGameTest",
      )
    }
    assertThrows(GradleException::class.java) {
      completedGameTests("smoke: OK", markers, "SmokeGameTest,LootConstraintBlockAllGameTest")
    }
    assertEquals(listOf("SmokeGameTest"), completedGameTests("smoke: OK", markers, "SmokeGameTest"))
  }
}
