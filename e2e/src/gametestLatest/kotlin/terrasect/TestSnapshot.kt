package terrasect.gametest

import de.skuzzle.test.snapshots.SnapshotFile
import de.skuzzle.test.snapshots.SnapshotFile.SnapshotHeader
import java.nio.file.Files
import java.nio.file.Path

fun testSnapshot(path: Path, actual: String, testClass: String, name: String): String {
  if (System.getProperty("updateSnapshots") != null) {
    Files.createDirectories(path.parent)
    val header =
      SnapshotHeader.fromMap(
        mapOf(
          SnapshotHeader.TEST_CLASS to testClass,
          SnapshotHeader.TEST_METHOD to "runTest",
          SnapshotHeader.SNAPSHOT_NUMBER to "0",
          SnapshotHeader.SNAPSHOT_NAME to name,
          SnapshotHeader.DYNAMIC_DIRECTORY to "true",
        )
      )
    SnapshotFile.of(header, actual).writeTo(path)
  }
  check(Files.isRegularFile(path)) {
    "Missing snapshot $path; create it explicitly with -PupdateSnapshots"
  }
  return SnapshotFile.fromSnapshotFile(path).snapshot()
}
