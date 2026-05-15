package terrasect.handler

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ScopedLogger(scope: String) {
  @PublishedApi internal val logger: Logger = LoggerFactory.getLogger(scope)

  inline fun traceBlock(action: () -> Unit) {
    if (logger.isTraceEnabled) action()
  }

  inline fun debugBlock(action: () -> Unit) {
    if (logger.isDebugEnabled) action()
  }

  inline fun trace(message: () -> String) {
    if (logger.isTraceEnabled) logger.trace(message())
  }

  inline fun debug(message: () -> String) {
    if (logger.isDebugEnabled) logger.debug(message())
  }

  inline fun info(message: () -> String) {
    if (logger.isInfoEnabled) logger.info(message())
  }

  inline fun warn(message: () -> String) {
    if (logger.isWarnEnabled) logger.warn(message())
  }
}

object NoiseScope {
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
