package terrasect.handler

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ScopedLogger(scope: String) {
  @PublishedApi internal val log: Logger = LoggerFactory.getLogger(scope)

  inline fun trace(message: () -> String) {
    if (log.isTraceEnabled) log.trace(message())
  }

  inline fun debug(message: () -> String) {
    if (log.isDebugEnabled) log.debug(message())
  }

  inline fun info(message: () -> String) {
    if (log.isInfoEnabled) log.info(message())
  }

  inline fun warn(message: () -> String) {
    if (log.isWarnEnabled) log.warn(message())
  }
}

object NoiseLogger {
  val root = ScopedLogger("noise")
  val registry = ScopedLogger("noise.registry")
  val dimension = ScopedLogger("noise.dimension")
  val context = ScopedLogger("noise.dimension.context")
  val handler = ScopedLogger("noise.handler")
  val densityFunction = ScopedLogger("noise.handler.densityFunction")
  val climate = ScopedLogger("noise.handler.climate")
  val originNoise = ScopedLogger("noise.handler.origin.noise")
  val originClimate = ScopedLogger("noise.handler.origin.climate")
}
