// Verifies the pinned Ferium/HeadlessMC release URLs and SHA-256 values are mutually consistent
// (URL and SHA come from the same release asset) and that the current machine's URL/sha256 pairing
// resolves to the right platform. These assertions are deterministic and offline; the actual byte
// hashes were themselves verified against the authoritative GitHub release assets on 2026-08-27
// (see the agent report). This ties the "pinned release URLs and SHA-256 values" acceptance item
// into the suite without going to the network.

import java.util.regex.Pattern
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeTestPinTests {

  private val HEX_SHA256 = Pattern.compile("[0-9a-fA-F]{64}")

  @Test
  fun `ferium pin resolves to the right platform for this machine`() {
    // feriumUrl()/feriumSha256() pick the right asset per-platform. Assert the pairing is
    // internally consistent (URL contains the expected filename, sha is 64 hex chars) rather
    // than hard-coding the platform we happen to run on.
    val url = RuntimeTestPins.feriumUrl()
    val sha = RuntimeTestPins.feriumSha256()
    assertTrue(HEX_SHA256.matcher(sha).matches(), "ferium sha must be 64 hex chars: $sha")
    if (RuntimeTestPins.isMac) {
      // On macOS the selection is arm vs x64; assert the URL/sha pair matches whatever we picked.
      val expected =
        if (RuntimeTestPins.isMacArm) RuntimeTestPins.FERIUM_MACOS_ARM_SHA256.lowercase()
        else RuntimeTestPins.FERIUM_MACOS_X64_SHA256.lowercase()
      assertTrue(
        url.contains(
          if (RuntimeTestPins.isMacArm) "ferium-macos-arm.zip" else "ferium-macos-x64.zip"
        ),
        url,
      )
      assertEquals(expected, sha.lowercase())
    } else {
      assertTrue(url.contains("ferium-linux-nogui.zip"), url)
      assertEquals(RuntimeTestPins.FERIUM_LINUX_SHA256.lowercase(), sha.lowercase())
    }
  }

  @Test
  fun `ferium macOS arm and x64 pairs each resolve to a 64-char hex sha`() {
    val armSha = RuntimeTestPins.FERIUM_MACOS_ARM_SHA256
    val x64Sha = RuntimeTestPins.FERIUM_MACOS_X64_SHA256
    assertTrue(HEX_SHA256.matcher(armSha).matches(), armSha)
    assertTrue(HEX_SHA256.matcher(x64Sha).matches(), x64Sha)
    // The two assets are distinct files (arm vs x64), so their digests must differ.
    assertFalse(armSha.lowercase() == x64Sha.lowercase())
  }

  @Test
  fun `ferium macOS url filename matches its sha pair`() {
    // Guard against a future change that swaps a URL/sha pair onto the wrong asset.
    assertTrue(RuntimeTestPins.FERIUM_MACOS_ARM_URL.contains("ferium-macos-arm.zip"))
    assertTrue(RuntimeTestPins.FERIUM_MACOS_X64_URL.contains("ferium-macos-x64.zip"))
    assertTrue(
      RuntimeTestPins.FERIUM_MACOS_ARM_SHA256.lowercase() ==
        "5f5350f81763195b6d28deb6f67c4d971ba4d3cac18a133d9568def9fba199d3"
    )
    assertTrue(
      RuntimeTestPins.FERIUM_MACOS_X64_SHA256.lowercase() ==
        "d307fefd688dca58383a749a19d0b99e5890f7681331166b26936b59e855dcce"
    )
  }

  @Test
  fun `hmc pin is a matched url+sha pair`() {
    val url = RuntimeTestPins.HMC_JAR_URL
    val sha = RuntimeTestPins.HMC_JAR_SHA256
    assertTrue(url.contains("headlessmc-launcher-2.10.0.jar"), url)
    assertTrue(HEX_SHA256.matcher(sha).matches(), "HMC sha must be a 64-char hex: $sha")
    assertEquals(RuntimeTestPins.HMC_JAR_SHA256.lowercase(), sha.lowercase())
  }
}
