package terrasect.testing

import java.io.File
import java.nio.file.Path

object SnapshotOutputPaths {
  private const val COLLAPSED_PREFIX = "terrasect"

  @JvmStatic
  fun forTestClass(testClass: Class<*>, vararg segments: String?): File {
    var path = Path.of("build", "test-snapshots").resolve(collapsedClassPath(testClass))
    for (segment in segments) {
      if (segment.isNullOrEmpty()) {
        continue
      }
      path = path.resolve(segment)
    }
    return path.toFile()
  }

  private fun collapsedClassPath(testClass: Class<*>): Path {
    var packageName = testClass.packageName
    if (packageName.startsWith(COLLAPSED_PREFIX)) {
      packageName = packageName.substring(COLLAPSED_PREFIX.length)
      if (packageName.startsWith(".")) {
        packageName = packageName.substring(1)
      }
    }
    return if (packageName.isEmpty()) {
      Path.of(testClass.simpleName)
    } else {
      Path.of(packageName.replace('.', File.separatorChar)).resolve(testClass.simpleName)
    }
  }
}
