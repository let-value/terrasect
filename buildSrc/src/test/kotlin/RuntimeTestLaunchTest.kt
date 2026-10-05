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
import org.junit.jupiter.params.provider.CsvSource

class RuntimeTestLaunchTest {
  @TempDir lateinit var directory: File

  @ParameterizedTest
  @CsvSource("false,false", "true,false", "false,true", "true,true")
  fun `fatal failure terminates client or server children and saves failed evidence`(
    parentExits: Boolean,
    serverSmoke: Boolean,
  ) {
    val source = File(directory, "LauncherProbe.java")
    source.writeText(
      """
      public class LauncherProbe {
        public static void main(String[] args) throws Exception {
          if ($serverSmoke && java.util.Arrays.asList(args).contains("add")) {
            java.nio.file.Path server = java.nio.file.Path.of("terrasect-runtime");
            java.nio.file.Files.createDirectories(server);
            java.nio.file.Files.writeString(server.resolve("run.sh"), "java @user_jvm_args.txt");
            return;
          }
          if ($serverSmoke && java.util.Arrays.asList(args).contains("cache")) return;
          if (!args[0].equals("child")) {
            String identity = "-Dterrasect.runtime.id=" + System.getProperty("terrasect.runtime.id");
            if ($serverSmoke) {
              try (var files = java.nio.file.Files.list(java.nio.file.Path.of("."))) {
                identity = "@" + files.filter(p -> p.toString().endsWith(".args")).findFirst()
                  .orElse(java.nio.file.Path.of("terrasect-runtime/user_jvm_args.txt")).toAbsolutePath().normalize();
              }
            }
            if ($serverSmoke) {
              java.nio.file.Files.writeString(java.nio.file.Path.of("server-ready"), identity.substring(1));
              while (!java.nio.file.Files.exists(java.nio.file.Path.of("child.pid"))) Thread.sleep(10);
            } else {
              Process child = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                identity, "-cp", System.getProperty("java.class.path"), "LauncherProbe", "child").start();
              java.nio.file.Files.writeString(java.nio.file.Path.of("child.pid"), Long.toString(child.pid()));
            }
            System.out.println("${if (serverSmoke) "Server smoke failed" else "Client gametests failed with an exception"}");
            if ($parentExits) return;
          }
          if (args[0].equals("child")) java.nio.file.Files.writeString(java.nio.file.Path.of("child.pid"), Long.toString(ProcessHandle.current().pid()));
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
    task.loader.set(if (serverSmoke) "neoforge" else "fabric")
    task.clientTests.set(!serverSmoke)
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

    val sibling =
      if (serverSmoke)
        Thread {
            val ready = File(runtime, "server-ready")
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15)
            while ((!ready.isFile || ready.length() == 0L) && System.nanoTime() < deadline) Thread
              .sleep(10)
            ProcessBuilder(
                File(System.getProperty("java.home"), "bin/java").path,
                "@" + ready.readText(),
                "-cp",
                jar.absolutePath,
                "LauncherProbe",
                "child",
              )
              .directory(runtime)
              .inheritIO()
              .start()
          }
          .apply { start() }
      else null

    val start = System.nanoTime()
    val failure = assertThrows(GradleException::class.java) { task.launch() }
    val marker = if (serverSmoke) "Server smoke failed" else "Client gametests failed"
    assertTrue(failure.message!!.contains(marker))
    assertTrue((System.nanoTime() - start) / 1_000_000_000 < 10)
    sibling?.join(1000)
    val child = ProcessHandle.of(File(runtime, "child.pid").readText().toLong())
    try {
      assertFalse(child.map { it.isAlive }.orElse(false))
    } finally {
      child.ifPresent { it.destroyForcibly() }
    }
    if (serverSmoke) {
      val argumentFile = runtime.listFiles()!!.single { it.extension == "args" }
      assertTrue(
        File(runtime, "terrasect-runtime/run.sh").readText().contains(argumentFile.canonicalPath)
      )
    }
    assertTrue(result.readText().contains("\"status\": \"failed\""))
    assertTrue(task.launchLog.get().asFile.readText().contains(marker))
  }
}
