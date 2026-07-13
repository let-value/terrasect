package terrasect.testing

import de.skuzzle.test.snapshots.SnapshotDirectory
import de.skuzzle.test.snapshots.SnapshotDirectoryStrategy
import java.io.File
import java.nio.file.Path

class VersionedSnapshotDirectoryStrategy : SnapshotDirectoryStrategy {
  override fun determineSnapshotDirectory(
    testClass: Class<*>,
    directory: SnapshotDirectory,
  ): Path {
    val packagePath = testClass.packageName.replace('.', File.separatorChar)
    return Path.of("src", "test", "resources")
      .resolve(SnapshotOutputPaths.minecraftVersion())
      .resolve(packagePath)
      .resolve("${testClass.simpleName}_snapshots")
  }
}
