import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import javax.tools.ToolProvider
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RuntimeTestLaunchTest {
  @TempDir lateinit var directory: File

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `fatal client failure terminates the game and its child and saves failed evidence`(
    parentExits: Boolean
  ) {
    val source = File(directory, "LauncherProbe.java")
    source.writeText(
      """
      public class LauncherProbe {
        public static void main(String[] args) throws Exception {
          if (!args[0].equals("child")) {
            Process child = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
              "-Dterrasect.runtime.id=" + System.getProperty("terrasect.runtime.id"),
              "-cp", System.getProperty("java.class.path"), "LauncherProbe", "child").start();
            java.nio.file.Files.writeString(java.nio.file.Path.of("child.pid"), Long.toString(child.pid()));
            System.out.println("Client gametests failed with an exception");
            if ($parentExits) return;
          }
          Thread.sleep(30000);
        }
      }
      """
        .trimIndent()
    )
    check(ToolProvider.getSystemJavaCompiler().run(null, null, null, source.path) == 0)
    val jar = File(directory, "launcher.jar")
    val manifest =
      Manifest().apply {
        mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        mainAttributes[Attributes.Name.MAIN_CLASS] = "LauncherProbe"
      }
    JarOutputStream(jar.outputStream(), manifest).use {
      it.putNextEntry(JarEntry("LauncherProbe.class"))
      File(directory, "LauncherProbe.class").inputStream().use { input -> input.copyTo(it) }
      it.closeEntry()
    }
    val project = ProjectBuilder.builder().withProjectDir(directory).build()
    val task = project.tasks.register("launchProbe", MinecraftTestLaunchTask::class.java).get()
    task.loader.set("fabric")
    task.minecraft.set("26.2")
    task.loaderVersion.set("0.19.3")
    task.javaVersion.set("25")
    task.gameJavaExecutable.set(File(System.getProperty("java.home"), "bin/java").path)
    task.scenario.set("build")
    task.completionMarkers.set(mapOf("SmokeGameTest" to "smoke: OK"))
    task.clientGametestMod.set("terrasect-e2e")
    task.testFilter.set("SmokeGameTest")
    task.e2eDirectory.set(directory.path)
    task.timeoutSeconds.set(15)
    task.launcher.set(jar)
    task.modpackDirectory.set(File(directory, "pack").apply { mkdirs() })
    task.minecraftDirectory.set(File(directory, "minecraft"))
    val runtime = File(directory, "runtime")
    task.runtimeDirectory.set(runtime)
    task.launchLog.set(File(directory, "launch.log"))
    val result = File(directory, "result.json")
    task.resultFile.set(result)

    val start = System.nanoTime()
    val failure = assertThrows(GradleException::class.java) { task.launch() }
    assertTrue(failure.message!!.contains("Client gametests failed"))
    assertTrue((System.nanoTime() - start) / 1_000_000_000 < 10)
    val child = ProcessHandle.of(File(runtime, "child.pid").readText().toLong())
    try {
      assertFalse(child.map { it.isAlive }.orElse(false))
    } finally {
      child.ifPresent { it.destroyForcibly() }
    }
    assertTrue(result.readText().contains("\"status\": \"failed\""))
    assertTrue(task.launchLog.get().asFile.readText().contains("Client gametests failed"))
  }
}
