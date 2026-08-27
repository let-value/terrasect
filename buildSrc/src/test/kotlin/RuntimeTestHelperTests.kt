// Offline unit tests for the buildSrc helper functions used by the HeadlessMC + Ferium bootstrap.
// These are deliberately pure: they build archives with the JDK ZipOutputStream and never touch the
// network, so they run fully offline and cover the hardening paths the card calls out — checksum
// mismatch, archive-path traversal (Zip Slip), and the missing-binary detection.

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeTestHelperTests {

  @Test
  fun `sha256 returns null for a missing file`() {
    assertNull(sha256(File("/does/not/exist.bin")))
  }

  @Test
  fun `sha256 matches the well-known digest of empty input`() {
    val tmp = File.createTempFile("empty-", ".bin")
    try {
      // SHA-256 of the empty string is a fixed, well-known constant.
      assertEquals(
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        sha256(tmp)!!,
      )
    } finally {
      tmp.delete()
    }
  }

  @Test
  fun `expectSha256 accepts a matching digest`() {
    val tmp = File.createTempFile("ok-", ".bin")
    try {
      tmp.writeText("hello")
      expectSha256(tmp, sha256(tmp)!!)
    } finally {
      tmp.delete()
    }
  }

  @Test
  fun `expectSha256 throws on a mismatched digest`() {
    val tmp = File.createTempFile("bad-", ".bin")
    try {
      tmp.writeText("hello")
      assertThrows(GradleException::class.java) { expectSha256(tmp, "deadbeef") }
    } finally {
      tmp.delete()
    }
  }

  @Test
  fun `isInside returns true for the ancestor and a direct child`() {
    val dest = File("/tmp/runtime-tools/bootstrap/hmc")
    val child = File(dest, "sub/dir/file.jar")
    assertTrue(isInside(dest, child), "child should be inside ancestor")
    // The ancestor is considered inside itself.
    assertTrue(isInside(dest, dest))
  }

  @Test
  fun `isInside returns false for an outside path`() {
    val dest = File("/tmp/runtime-tools/bootstrap/hmc")
    val outside = File("/etc/passwd")
    assertFalse(isInside(dest, outside), "outside path must not be reported inside")
  }

  /** Builds a zip with one (optionally malicious) entry, using the JDK ZipOutputStream. */
  private fun buildZip(file: File, contents: Map<String, String>) {
    ZipOutputStream(file.outputStream()).use { out ->
      contents.forEach { (name, data) ->
        out.putNextEntry(ZipEntry(name))
        out.write(data.toByteArray())
        out.closeEntry()
      }
    }
  }

  @Test
  fun `unzip extracts a normal entry`() {
    val dest =
      File.createTempFile("dest", "").apply {
        delete()
        mkdirs()
      }
    try {
      val zip = File.createTempFile("okzip", ".zip")
      try {
        buildZip(zip, mapOf("ferium" to "executable-bytes"))
        unzip(zip, dest)
        val extracted = File(dest, "ferium")
        assertTrue(extracted.isFile, "ferium binary should be extracted")
        assertEquals("executable-bytes", extracted.readText())
      } finally {
        zip.delete()
      }
    } finally {
      dest.deleteRecursively()
    }
  }

  @Test
  fun `unzip rejects a path-traversal entry (Zip Slip)`() {
    val dest =
      File.createTempFile("dest2", "").apply {
        delete()
        mkdirs()
      }
    try {
      val zip = File.createTempFile("evilzip", ".zip")
      try {
        // A canonical-path-escaping name; the old string-prefix guard (startsWith without a
        // trailing separator) would have let this through.
        buildZip(zip, mapOf("../escape.txt" to "pwned"))
        assertThrows(GradleException::class.java) { unzip(zip, dest) }
        assertFalse(File(dest, "../escape.txt").exists(), "no file should escape the destination")
      } finally {
        zip.delete()
      }
    } finally {
      dest.deleteRecursively()
    }
  }

  @Test
  fun `findFeriumBinary returns the exact-named binary`() {
    val dest =
      File.createTempFile("fer", "").apply {
        delete()
        mkdirs()
      }
    try {
      val binary = File(dest, "ferium").apply { writeText("v") }
      assertEquals(binary.name, findFeriumBinary(dest).name)
    } finally {
      dest.deleteRecursively()
    }
  }

  @Test
  fun `findFeriumBinary throws when the exact binary is missing`() {
    val dest =
      File.createTempFile("fe2", "").apply {
        delete()
        mkdirs()
      }
    try {
      // A file with the wrong name should not satisfy the search.
      File(dest, "ferium-real").apply { writeText("v") }
      assertThrows(GradleException::class.java) { findFeriumBinary(dest) }
    } finally {
      dest.deleteRecursively()
    }
  }

  @Test
  fun `ferium entry with a leading-dot name is rejected`() {
    val dest =
      File.createTempFile("fe3", "").apply {
        delete()
        mkdirs()
      }
    try {
      val zip = File.createTempFile("fe3zip", ".zip")
      try {
        // The zip contains only a file that is not named exactly `ferium`.
        buildZip(zip, mapOf(".ferium" to "x", "feriumx" to "y"))
        assertThrows(GradleException::class.java) { findFeriumBinary(dest) }
      } finally {
        zip.delete()
      }
    } finally {
      dest.deleteRecursively()
    }
  }
}
