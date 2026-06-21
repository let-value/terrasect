package terrasect

import java.nio.file.Path

internal fun e2eModuleDir(anchor: Class<*>): Path {
  val configured = System.getProperty("terrasect.e2eDir")?.takeIf { it.isNotBlank() }
  if (configured != null) {
    return Path.of(configured)
  }

  val classesRoot = Path.of(anchor.protectionDomain.codeSource.location.toURI())
  return classesRoot.parent.parent.parent.parent
}

internal fun e2eScreenshotsBase(anchor: Class<*>): Path =
  e2eModuleDir(anchor).resolve("build/gametest-screenshots")
