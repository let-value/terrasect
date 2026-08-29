import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

private fun File.sha256(): String {
  val digest = MessageDigest.getInstance("SHA-256")
  inputStream().use { input ->
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
      val count = input.read(buffer)
      if (count < 0) break
      digest.update(buffer, 0, count)
    }
  }
  return digest.digest().joinToString("") { "%02x".format(it) }
}

@CacheableTask
abstract class MinecraftTestBootstrapTask : DefaultTask() {
  @get:Input abstract val url: Property<String>
  @get:Input abstract val checksum: Property<String>
  @get:OutputFile abstract val launcher: RegularFileProperty
  @get:Input abstract val feriumUrl: Property<String>
  @get:Input abstract val feriumChecksum: Property<String>
  @get:OutputFile abstract val ferium: RegularFileProperty

  @TaskAction
  fun download() {
    download(url.get(), checksum.get(), launcher.get().asFile, "HeadlessMC")
    val archive = File(temporaryDir, "ferium.zip")
    download(feriumUrl.get(), feriumChecksum.get(), archive, "Ferium")
    val output = ferium.get().asFile
    output.parentFile.mkdirs()
    ZipInputStream(archive.inputStream()).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        if (!entry.isDirectory && entry.name.substringAfterLast('/') == "ferium") {
          output.outputStream().use(zip::copyTo)
          break
        }
      }
    }
    if (!output.isFile) throw GradleException("Ferium executable was not found in $archive")
    output.setExecutable(true)
  }

  private fun download(source: String, expected: String, output: File, tool: String) {
    output.parentFile.mkdirs()
    val temporary = File(output.parentFile, "${output.name}.part")
    val connection = URI(source).toURL().openConnection()
    connection.connectTimeout = 30_000
    connection.readTimeout = 120_000
    connection.getInputStream().use { input ->
      temporary.outputStream().use { destination -> input.copyTo(destination) }
    }
    val actual = temporary.sha256()
    if (!actual.equals(expected, ignoreCase = true)) {
      temporary.delete()
      throw GradleException("$tool checksum mismatch: expected $expected, got $actual")
    }
    temporary.copyTo(output, overwrite = true)
    temporary.delete()
  }
}

@CacheableTask
abstract class MinecraftTestPrepareTask : DefaultTask() {
  @get:Classpath abstract val dependencies: ConfigurableFileCollection
  @get:Classpath abstract val artifacts: ConfigurableFileCollection
  @get:Input abstract val loader: Property<String>
  @get:Input abstract val minecraft: Property<String>
  @get:Input abstract val resolveWithFerium: Property<Boolean>
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val modpackDefinition: RegularFileProperty
  @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val ferium: RegularFileProperty
  @get:OutputDirectory abstract val modpackDirectory: DirectoryProperty

  @TaskAction
  fun prepare() {
    val output = modpackDirectory.get().asFile
    output.deleteRecursively()
    output.mkdirs()
    modpackDefinition.get().asFile.copyTo(File(output, "modpack.json"))
    val user = File(output, "user").apply { mkdirs() }
    val jars = (dependencies.files + artifacts.files).filter { it.extension == "jar" }
    jars
      .sortedBy { it.name }
      .forEach { source ->
        val destination = File(user, source.name)
        if (destination.exists()) {
          throw GradleException("Duplicate mod filename ${source.name} in $path")
        }
        source.copyTo(destination)
      }
    if (resolveWithFerium.get()) {
      val process =
        ProcessBuilder(
            ferium.get().asFile.absolutePath,
            "--config-file",
            File(output, "modpack.json").absolutePath,
            "upgrade",
          )
          .directory(output)
          .redirectErrorStream(true)
          .apply {
            val certs =
              listOf("/etc/ssl/cert.pem", "/etc/ssl/certs/ca-certificates.crt")
                .map(::File)
                .firstOrNull(File::isFile)
            if (certs != null) environment()["SSL_CERT_FILE"] = certs.absolutePath
          }
          .start()
      val feriumOutput = StringBuilder()
      val reader =
        Thread {
            process.inputStream.bufferedReader().useLines { lines ->
              lines.forEach {
                feriumOutput.appendLine(it)
                logger.lifecycle(it)
              }
            }
          }
          .apply { start() }
      if (!process.waitFor(10, TimeUnit.MINUTES)) {
        process.destroyForcibly()
        throw GradleException("Ferium timed out for ${loader.get()} ${minecraft.get()}")
      }
      reader.join(30_000)
      if (process.exitValue() != 0) {
        throw GradleException(
          "Ferium exited ${process.exitValue()} for $path\n${feriumOutput.toString().trim()}"
        )
      }
    } else {
      user.listFiles().orEmpty().forEach { it.copyTo(File(output, it.name)) }
    }
    user.deleteRecursively()
    val count = output.listFiles().orEmpty().count { it.extension == "jar" }
    if (count == 0) throw GradleException("No mods were prepared by $path")
    logger.lifecycle("Prepared $count mod(s) in $output")
  }
}

abstract class MinecraftTestLaunchTask : DefaultTask() {
  @get:Input abstract val loader: Property<String>
  @get:Input abstract val minecraft: Property<String>
  @get:Input abstract val successMarker: Property<String>
  @get:Input abstract val clientGametestMod: Property<String>
  @get:Input abstract val testFilter: Property<String>
  @get:Input abstract val e2eDirectory: Property<String>
  @get:Input abstract val timeoutSeconds: Property<Long>
  @get:InputFile abstract val launcher: RegularFileProperty
  @get:InputDirectory abstract val modpackDirectory: DirectoryProperty
  @get:LocalState abstract val minecraftDirectory: DirectoryProperty
  @get:LocalState abstract val runtimeDirectory: DirectoryProperty
  @get:OutputFile abstract val launchLog: RegularFileProperty

  @TaskAction
  fun launch() {
    val runtime = runtimeDirectory.get().asFile.apply { mkdirs() }
    val minecraftHome = minecraftDirectory.get().asFile.apply { mkdirs() }
    val mods = File(runtime, "mods")
    mods.deleteRecursively()
    modpackDirectory.get().asFile.copyRecursively(mods, overwrite = true)

    val java = File(System.getProperty("java.home"), "bin/java").absolutePath
    val gameJvmArguments = buildList {
      add("-Djava.awt.headless=true")
      if (clientGametestMod.get().isNotEmpty()) {
        add("-Dfabric.client.gametest=true")
        add("-Dfabric.client.gametest.modid=${clientGametestMod.get()}")
        add("-Dterrasect.e2eDir=${e2eDirectory.get()}")
        if (testFilter.get().isNotEmpty()) add("-Dtest=${testFilter.get()}")
      }
    }
    val command =
      listOf(
        java,
        "-Dhmc.gamedir=${runtime.absolutePath}",
        "-Dhmc.mcdir=${minecraftHome.absolutePath}",
        "-Dhmc.offline=true",
        "-Dhmc.assets.dummy=true",
        "-Dhmc.jline.enabled=false",
        "-Dhmc.rethrow.launch.exceptions=true",
        "-Dhmc.exit.on.failed.command=true",
        "-Dhmc.jvmargs=${gameJvmArguments.joinToString(" ")}",
        "-jar",
        launcher.get().asFile.absolutePath,
        "--command",
        "launch",
        "${loader.get()}:${minecraft.get()}",
        "-lwjgl",
      )
    val process = ProcessBuilder(command).directory(runtime).redirectErrorStream(true).start()
    val output = StringBuilder()
    val markerSeen = AtomicBoolean(false)
    val reader =
      Thread {
          try {
            process.inputStream.bufferedReader().useLines { lines ->
              lines.forEach {
                output.appendLine(it)
                logger.lifecycle(it)
                if (
                  successMarker.get().isNotEmpty() &&
                    it.contains(successMarker.get()) &&
                    markerSeen.compareAndSet(false, true)
                ) {
                  process.descendants().forEach(ProcessHandle::destroy)
                  process.destroy()
                }
              }
            }
          } catch (error: IOException) {
            if (!markerSeen.get()) output.appendLine("HeadlessMC output failed: ${error.message}")
          }
        }
        .apply { start() }
    val finished = process.waitFor(timeoutSeconds.get(), TimeUnit.SECONDS)
    if (!finished) {
      process.descendants().forEach(ProcessHandle::destroyForcibly)
      process.destroyForcibly()
    }
    reader.join(30_000)

    val log = launchLog.get().asFile
    log.parentFile.mkdirs()
    log.writeText(output.toString())
    if (!finished)
      throw GradleException("HeadlessMC timed out for ${loader.get()} ${minecraft.get()}")
    if (process.exitValue() != 0) {
      throw GradleException(
        "HeadlessMC exited ${process.exitValue()} for ${loader.get()} ${minecraft.get()}"
      )
    }
    if (successMarker.get().isNotEmpty() && !markerSeen.get()) {
      throw GradleException("'${successMarker.get()}' was not found in ${log.absolutePath}")
    }
  }
}
