import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

val loader = project.parent!!.name
val sets = extensions.getByType<SourceSetContainer>()
val smoke = sets.create("serverSmoke")
val main = sets.getByName("main")

smoke.compileClasspath += main.compileClasspath + main.output

smoke.java.srcDir(rootProject.file("e2e/src/server-$loader/java"))

smoke.resources.srcDir(rootProject.file("e2e/src/server-$loader/resources"))

extensions
  .getByType<KotlinJvmProjectExtension>()
  .sourceSets
  .getByName("serverSmoke")
  .kotlin
  .srcDir(rootProject.file("e2e/src/server/kotlin"))

tasks.named<ProcessResources>(smoke.processResourcesTaskName) {
  inputs.property("version", project.version)
  filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) {
    expand("version" to project.version)
  }
}

val thin =
  tasks.register<Jar>("serverSmokeJar") {
    archiveBaseName.set("terrasect-server-tests-$loader")
    archiveClassifier.set("gametest-dev")
    from(smoke.output)
  }

if (loader != "fabric" || project.version.toString().contains("+26.")) {
  thin.configure { archiveClassifier.set("gametest") }
  tasks.register("serverSmokeModJar") { dependsOn(thin) }
}
