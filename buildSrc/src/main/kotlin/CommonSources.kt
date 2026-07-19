import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import java.io.File
import org.gradle.api.Project

fun Project.processCommonSourceTree(sc: StonecutterBuildExtension, commonDir: File) {
  fun process(srcDir: File, extension: String, outDir: String) =
    fileTree(srcDir) { include("**/*.$extension") }
      .forEach { file ->
        sc.process(file, "$outDir/${file.relativeTo(srcDir).path}")
      }

  process(commonDir.resolve("src/main/kotlin"), "kt", "build/processed/main/kotlin")
  process(commonDir.resolve("src/main/java"), "java", "build/processed/main/java")
}
