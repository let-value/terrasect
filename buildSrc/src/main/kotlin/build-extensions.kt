import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import java.io.File
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

val Project.sc: StonecutterBuildExtension
  get() = extensions.getByType<StonecutterBuildExtension>()

fun Project.prop(key: String): String = sc.properties[key]

fun Project.propOrNull(key: String): String? = sc.properties.getOrNull<String>(key)

fun Project.dep(key: String): String = prop("deps.$key")

fun Project.depOrNull(key: String): String? = propOrNull("deps.$key")

val Project.mcVersion: String
  get() = sc.current.version

val Project.loader: String
  get() = requireNotNull(parent?.name) { "No branch parent for $path" }

val Project.commonProject: Project
  get() = project(":common:$name")

val Project.commonDir: File
  get() = rootProject.file("common")

val Project.accessWidenerFile: String
  get() = "$mcVersion.accesswidener"

val Project.isLatest: Boolean
  get() = mcVersion == mod.latest

val Project.legacyLoomCommon: Boolean
  get() = mcVersion == "1.20.1"

val Project.mod: ModData
  get() = ModData(this)

class ModData(private val project: Project) {
  val id
    get() = project.prop("mod.id")

  val name
    get() = project.prop("mod.name")

  val version
    get() = project.prop("mod.version")

  val group
    get() = project.prop("mod.group")

  val license
    get() = project.prop("mod.license")

  val authors
    get() = project.prop("mod.authors")

  val description
    get() = project.prop("mod.description")

  val latest
    get() = project.prop("mod.latest")
}

/**
 * Shared `processResources` expansion table for the Fabric-family branches (fabric, e2e,
 * e2e-compat). Extra branch-specific keys (gametest entrypoints, mixin compat level, ...) are
 * appended via [extra].
 */
fun Project.fabricResourceProps(vararg extra: Pair<String, String>): Map<String, String> =
  mapOf(
    "version" to version.toString(),
    "mod_id" to mod.id,
    "mod_name" to mod.name,
    "mod_description" to mod.description,
    "mod_authors" to mod.authors,
    "mod_license" to mod.license,
    "fabric_loader_version" to dep("fabric_loader"),
    "minecraft_version" to mcVersion,
    "java_version" to prop("java"),
    "fabric_api_version" to dep("fabric_api"),
    "fabric_kotlin_version" to dep("fabric_kotlin"),
    "access_widener_file" to accessWidenerFile,
  ) + extra
