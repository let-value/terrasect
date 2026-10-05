import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Optional
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
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NAME_ONLY)
  abstract val dependencies: ConfigurableFileCollection
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NAME_ONLY)
  abstract val artifacts: ConfigurableFileCollection
  @get:Input abstract val loader: Property<String>
  @get:Input abstract val minecraft: Property<String>
  @get:Input abstract val loaderVersion: Property<String>
  @get:Input abstract val requestedDependencies: ListProperty<String>
  @get:Input abstract val resolveWithFerium: Property<Boolean>
  @get:Optional
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val modpackDefinition: RegularFileProperty
  @get:Optional
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val ferium: RegularFileProperty
  @get:OutputDirectory abstract val modpackDirectory: DirectoryProperty

  @TaskAction
  fun prepare() {
    val output = modpackDirectory.get().asFile
    output.deleteRecursively()
    output.mkdirs()
    val definition = modpackDefinition.orNull?.asFile
    if (resolveWithFerium.get()) {
      if (definition == null)
        throw GradleException("A Ferium modpack definition is required for $path")
      definition.copyTo(File(output, "modpack.json"))
    }
    val jars = (dependencies.files + artifacts.files).filter { it.extension == "jar" }.distinct()
    val exactMods = jars.mapNotNull(::modIds).flatten().toSet()
    if (resolveWithFerium.get()) {
      val process =
        ProcessBuilder(
            ferium.orNull?.asFile?.absolutePath
              ?: throw GradleException("Ferium is required for $path"),
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
    }
    output
      .listFiles()
      .orEmpty()
      .filter { it.extension == "jar" }
      .forEach { selected ->
        if (modIds(selected).any(exactMods::contains)) selected.delete()
      }
    jars
      .sortedBy { it.name }
      .forEach { source ->
        val destination = File(output, source.name)
        if (destination.exists()) {
          throw GradleException("Duplicate mod filename ${source.name} in $path")
        }
        source.copyTo(destination)
      }
    val finalJars =
      output.listFiles().orEmpty().filter { it.extension == "jar" }.sortedBy { it.name }
    val modRecords = finalJars.map { jar ->
      val ids = modIds(jar)
      mapOf(
        "file" to jar.name,
        "modIds" to ids,
        "sha256" to jar.sha256(),
        "role" to jarRole(jar, ids),
      )
    }
    val manifest =
      mapOf(
        "loader" to loader.get(),
        "loaderVersion" to loaderVersion.get(),
        "minecraft" to minecraft.get(),
        "requestedDependencies" to requestedDependencies.get(),
        "modpackDefinition" to definition?.absolutePath,
        "modpackDefinitionSha256" to definition?.sha256(),
        "productionJars" to modRecords.filter { it["role"] == "production" },
        "testModJars" to modRecords.filter { it["role"] == "test-mod" },
        "thirdPartyMods" to modRecords.filter { it["role"] == "third-party" },
        "jars" to modRecords,
      )
    File(output, "runtime-test-manifest.json")
      .writeText(JsonOutput.prettyPrint(JsonOutput.toJson(manifest)) + "\n")
    val count = finalJars.size
    if (count == 0) throw GradleException("No mods were prepared by $path")
    logger.lifecycle("Prepared $count mod(s) in $output")
  }

  private fun modIds(jar: File): List<String> =
    runCatching {
        ZipFile(jar).use { zip ->
          val fabric = zip.getEntry("fabric.mod.json")
          if (fabric != null) {
            val metadata = zip.getInputStream(fabric).bufferedReader().use { it.readText() }
            Regex(""""id"\s*:\s*"([^"]+)"""")
              .find(metadata)
              ?.groupValues
              ?.get(1)
              ?.let(::listOf)
              .orEmpty()
          } else {
            val neoForge = zip.getEntry("META-INF/neoforge.mods.toml")
            if (neoForge == null) emptyList()
            else {
              val metadata = zip.getInputStream(neoForge).bufferedReader().use { it.readText() }
              Regex("""modId\s*=\s*"([^"]+)"""")
                .findAll(metadata)
                .map {
                  it.groupValues[1]
                }
                .toList()
            }
          }
        }
      }
      .getOrDefault(emptyList())

  private fun jarRole(jar: File, ids: List<String>): String {
    val name = jar.name.lowercase()
    return when {
      ids.any {
        it.startsWith("terrasect-e2e") ||
          it == "terrasect-server-tests" ||
          it == "terrasect_server_tests"
      } -> "test-mod"
      "terrasect" in ids -> "production"
      ids.any { it in RUNTIME_MOD_IDS } || RUNTIME_JAR_NAMES.any(name::contains) ->
        "runtime-dependency"
      else -> "third-party"
    }
  }

  private companion object {
    val RUNTIME_MOD_IDS =
      setOf(
        "fabric-api",
        "fabric-client-gametest-api-v1",
        "fabric-language-kotlin",
        "connector",
        "forgified-fabric-api",
        "forgified-fabric-loader",
        "launchpad",
        "kotlinforforge",
        "kfflang",
        "kfflib",
        "kffmod",
      )
    val RUNTIME_JAR_NAMES =
      listOf(
        "fabric-api",
        "fabric-client-gametest",
        "fabric-language-kotlin",
        "connector",
        "transformer",
        "forgified-fabric",
        "launchpad",
        "kotlinforforge",
        "kff",
      )
  }
}

abstract class MinecraftTestLaunchTask : DefaultTask() {
  @get:Input abstract val clientTests: Property<Boolean>

  init {
    clientTests.convention(true)
  }

  @get:Input abstract val loader: Property<String>
  @get:Input abstract val minecraft: Property<String>
  @get:Input abstract val loaderVersion: Property<String>
  @get:Input abstract val javaVersion: Property<String>
  @get:Input abstract val gameJavaExecutable: Property<String>
  @get:Input abstract val scenario: Property<String>
  @get:Input abstract val completionMarkers: MapProperty<String, String>
  @get:Input abstract val clientGametestMod: Property<String>
  @get:Input abstract val testFilter: Property<String>
  @get:Input abstract val e2eDirectory: Property<String>
  @get:Input abstract val timeoutSeconds: Property<Long>
  @get:InputFile abstract val launcher: RegularFileProperty
  @get:InputDirectory abstract val modpackDirectory: DirectoryProperty
  @get:LocalState abstract val minecraftDirectory: DirectoryProperty
  @get:LocalState abstract val runtimeDirectory: DirectoryProperty
  @get:OutputFile abstract val launchLog: RegularFileProperty
  @get:OutputFile abstract val resultFile: RegularFileProperty

  @TaskAction
  fun launch() {
    val runtime =
      runtimeDirectory.get().asFile.apply {
        deleteRecursively()
        mkdirs()
      }
    val minecraftHome = minecraftDirectory.get().asFile.apply { mkdirs() }
    val pack = modpackDirectory.get().asFile
    val jars = pack.listFiles().orEmpty().filter { it.extension == "jar" }
    val output = StringBuilder()
    var processExitCode: Int? = null
    var status = "failed"
    var failure: String? = null
    try {
      output.appendLine("loader: ${loader.get()} ${loaderVersion.get()}")
      output.appendLine("minecraft: ${minecraft.get()}")
      output.appendLine("scenario: ${scenario.get()}")
      output.appendLine("modpack: $pack")
      output.appendLine("test filter: ${testFilter.get()}")
      if (loader.get() == "neoforge") {
        File(runtime, "config/fml.toml").apply {
          parentFile.mkdirs()
          writeText("earlyWindowControl=false\n")
        }
      }
      if (jars.any { it.name.contains("distanthorizons", ignoreCase = true) }) {
        File(runtime, "config/DistantHorizons.toml").apply {
          parentFile.mkdirs()
          writeText("[client.advanced.debugging]\nrendererMode=\"DISABLED\"\n")
        }
      }
      val mods =
        File(runtime, "mods").apply {
          deleteRecursively()
          mkdirs()
        }
      jars.forEach { it.copyTo(File(mods, it.name), overwrite = true) }
      val gameJvmArguments = buildList {
        add("-Djava.awt.headless=true")
        if (clientTests.get()) {
          add("-Dfabric.client.gametest=true")
          add("-Dfabric.client.gametest.modid=${clientGametestMod.get()}")
          add("-Dterrasect.e2eDir=${e2eDirectory.get()}")
          if (testFilter.get().isNotEmpty()) add("-Dtest=${testFilter.get()}")
        } else add("-Dterrasect.serverSmoke=true")
      }
      if (!clientTests.get()) {
        runHmc(
          runtime,
          minecraftHome,
          listOf(
            "server",
            "add",
            loader.get(),
            minecraft.get(),
            "terrasect-runtime",
            loaderVersion.get(),
          ),
          output,
          emptyList(),
        )
        runHmc(
          runtime,
          minecraftHome,
          listOf("server", "cache", "terrasect-runtime"),
          output,
          emptyList(),
        )
        val server =
          runtime.walkTopDown().single { it.isDirectory && it.name == "terrasect-runtime" }
        val serverMods = File(server, "mods").apply { mkdirs() }
        jars.forEach { it.copyTo(File(serverMods, it.name), overwrite = true) }
        File(server, "eula.txt").writeText("eula=true\n")
        File(server, "server.properties")
          .writeText(
            "online-mode=false\nserver-port=0\nview-distance=2\nsimulation-distance=2\nlevel-seed=terrasect-server-smoke\n"
          )
      }
      processExitCode =
        runHmc(
          runtime,
          minecraftHome,
          if (clientTests.get())
            listOf("launch", "${loader.get()}:${minecraft.get()}:${loaderVersion.get()}", "-lwjgl")
          else listOf("server", "launch", "terrasect-runtime"),
          output,
          gameJvmArguments,
        )

      val logText = output.toString()
      val crashReports =
        runtime.walkTopDown().filter { it.isFile && it.parentFile.name == "crash-reports" }.toList()
      if (crashReports.isNotEmpty()) {
        throw GradleException("Runtime crash report(s) found for $path: $crashReports")
      }
      val executed = completedGameTests(logText, completionMarkers.get(), testFilter.get())
      status = "passed"
      logger.lifecycle(
        "${if (clientTests.get()) "Client tests" else "Server smoke"} passed: ${loader.get()} ${minecraft.get()} ${scenario.get()} $executed"
      )
    } catch (error: Throwable) {
      failure = error.message ?: error.javaClass.name
      output.appendLine("runtime failure: $failure")
      throw error
    } finally {
      val log = launchLog.get().asFile
      log.parentFile.mkdirs()
      log.writeText(output.toString())
      val manifestFile = File(pack, "runtime-test-manifest.json")
      val manifest =
        if (manifestFile.isFile) JsonSlurper().parseText(manifestFile.readText())
        else emptyMap<String, Any>()
      val result =
        mapOf(
          "loader" to loader.get(),
          "loaderVersion" to loaderVersion.get(),
          "minecraft" to minecraft.get(),
          "javaVersion" to javaVersion.get(),
          "scenario" to scenario.get(),
          "testMode" to if (clientTests.get()) "client" else "server",
          "graphics" to
            if (clientTests.get()) "HeadlessMC LWJGL stubs; Distant Horizons rendering disabled"
            else "Dedicated Minecraft server; no client rendering coverage",
          "testModId" to clientGametestMod.get(),
          "testFilter" to testFilter.get(),
          "expectedTests" to expectedGameTests(completionMarkers.get(), testFilter.get()).keys,
          "executedTests" to
            matchedGameTests(output.toString(), completionMarkers.get(), testFilter.get()),
          "exitCode" to processExitCode,
          "status" to status,
          "failure" to failure,
          "modpack" to manifest,
          "log" to log.absolutePath,
        )
      val resultFile = resultFile.get().asFile
      resultFile.parentFile.mkdirs()
      resultFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(result)) + "\n")
    }
  }

  private fun runHmc(
    runtime: File,
    minecraftHome: File,
    hmcArguments: List<String>,
    output: StringBuilder,
    jvmArguments: List<String>,
  ): Int {
    val java = File(System.getProperty("java.home"), "bin/java").absolutePath
    val runtimeId = "-Dterrasect.runtime.id=${UUID.randomUUID()}"
    val serverArgument =
      if (
        !clientTests.get() && loader.get() == "neoforge" && hmcArguments.getOrNull(1) == "launch"
      ) {
        val server =
          runtime.walkTopDown().single { it.isDirectory && it.name == "terrasect-runtime" }
        val argumentFile = File(runtime, "server-${UUID.randomUUID()}.args").canonicalFile
        argumentFile.writeText((jvmArguments + runtimeId).joinToString("\n") + "\n")
        val script = File(server, "run.sh")
        val contents = script.readText()
        check(contents.contains("@user_jvm_args.txt")) {
          "NeoForge launch script has no JVM argfile"
        }
        script.writeText(
          contents.replace("@user_jvm_args.txt", "@\"${argumentFile.absolutePath}\"")
        )
        "@${argumentFile.absolutePath}"
      } else null
    val command = buildList {
      add(java)
      add(runtimeId)
      add("-Dhmc.gamedir=${runtime.absolutePath}")
      add("-Dhmc.mcdir=${minecraftHome.absolutePath}")
      add("-Dhmc.java.versions=${gameJavaExecutable.get()}")
      add("-Dhmc.java.use.current=false")
      add("-Dhmc.offline=true")
      add("-Dhmc.assets.dummy=true")
      if (!clientTests.get()) {
        if (hmcArguments.getOrNull(1) != "launch") add("-Dhmc.server.test=true")
        add("-Dhmc.server.test.cache=true")
        add("-Dhmc.server.test.cache.use.mc.dir=true")
      }
      add("-Dhmc.jline.enabled=false")
      add("-Dhmc.rethrow.launch.exceptions=true")
      add("-Dhmc.exit.on.failed.command=true")
      add("-Dhmc.jvmargs=${(jvmArguments + runtimeId).joinToString(" ")}")
      add("-jar")
      add(launcher.get().asFile.absolutePath)
      add("--command")
      addAll(hmcArguments)
      if (!clientTests.get() && hmcArguments.getOrNull(1) == "launch" && loader.get() == "fabric") {
        add("--jvm")
        add("\"${(jvmArguments + runtimeId).joinToString(" ")}\"")
      }
    }
    output.appendLine("command: ${command.joinToString(" ")}")
    val process = ProcessBuilder(command).directory(runtime).redirectErrorStream(true).start()
    val fatal = AtomicReference<Pair<String, Long>>()
    val reader =
      Thread {
          try {
            process.inputStream.bufferedReader().useLines { lines ->
              lines.forEach {
                output.appendLine(it)
                logger.lifecycle(it)
                runtimeFailure(it)?.let { reason ->
                  fatal.compareAndSet(null, reason to System.nanoTime())
                }
              }
            }
          } catch (error: IOException) {
            output.appendLine("HeadlessMC output failed: ${error.message}")
          }
        }
        .apply { start() }
    var finished = false
    val children = mutableSetOf<ProcessHandle>()
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds.get())
    try {
      while (!finished && System.nanoTime() < deadline) {
        children.addAll(process.descendants().toList())
        finished = process.waitFor(200, TimeUnit.MILLISECONDS)
        val detected = fatal.get()
        if (detected != null && System.nanoTime() - detected.second > TimeUnit.SECONDS.toNanos(3))
          break
      }
    } finally {
      children.addAll(process.descendants().toList())
      children.addAll(
        ProcessHandle.allProcesses()
          .filter {
            val arguments = it.info().arguments().orElse(emptyArray())
            arguments.contains(runtimeId) ||
              (serverArgument != null && arguments.contains(serverArgument))
          }
          .toList()
      )
      children.filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly)
      if (process.isAlive) {
        process.destroyForcibly()
        process.waitFor(10, TimeUnit.SECONDS)
      }
      children.forEach { runCatching { it.onExit().get(5, TimeUnit.SECONDS) } }
      reader.join(30_000)
    }
    if (!finished) {
      fatal.get()?.let { throw GradleException(it.first) }
      throw GradleException(
        "HeadlessMC timed out for ${loader.get()} ${minecraft.get()} ${scenario.get()} after ${timeoutSeconds.get()} seconds"
      )
    }
    val exitCode = process.exitValue()
    if (exitCode != 0) {
      throw GradleException(
        "HeadlessMC exited $exitCode for ${loader.get()} ${minecraft.get()} ${scenario.get()}"
      )
    }
    return exitCode
  }
}
