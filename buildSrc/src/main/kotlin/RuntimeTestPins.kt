import java.util.Properties

private fun loadMinecraftTestProperties(): Properties =
  Properties().apply {
    val resource =
      MinecraftTestPins::class.java.getResourceAsStream("/minecraft-test.properties")
        ?: error("Missing buildSrc resource minecraft-test.properties")
    resource.use(::load)
  }

private fun Properties.required(key: String): String =
  getProperty(key) ?: error("Missing minecraft-test.properties entry: $key")

private fun Properties.requiredList(key: String): List<String> =
  required(key).split(',').map(String::trim).filter(String::isNotEmpty)

private fun Properties.requiredUrl(key: String, versionKey: String): String =
  required(key).replace("{version}", required(versionKey))

object MinecraftTestPins {
  private val properties = loadMinecraftTestProperties()

  private val osName = System.getProperty("os.name").lowercase()
  private val osArchitecture = System.getProperty("os.arch").lowercase()
  private val isMac = osName.contains(properties.required("platform.mac.marker"))
  private val isMacArm =
    isMac && properties.requiredList("platform.arm.markers").any(osArchitecture::contains)
  private val feriumPlatform =
    when {
      isMacArm -> properties.required("platform.mac-arm.key")
      isMac -> properties.required("platform.mac-x64.key")
      else -> properties.required("platform.linux.key")
    }

  val hmcUrl: String
    get() = properties.requiredUrl("headlessmc.url", "headlessmc.version")

  val hmcSha256: String
    get() = properties.required("headlessmc.sha256")

  val feriumUrl: String
    get() = properties.requiredUrl("ferium.url.$feriumPlatform", "ferium.version")

  val feriumSha256: String
    get() = properties.required("ferium.sha256.$feriumPlatform")
}
