package terrasect.handler

object NoiseDebug {
  @Volatile var enabled: Boolean = System.getProperty("terrasect.noiseDebug") != null

  inline fun ifEnabled(action: () -> Unit) {
    if (enabled) action()
  }
}
