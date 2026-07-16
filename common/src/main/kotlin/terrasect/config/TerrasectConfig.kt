package terrasect.config

import terrasect.instrumentation.TerrasectInstrScope
import terrasect.instrumentation.TerrasectMetricEvent

data class LoggingConfig(
  val loadSummary: Boolean = true,
  val validationWarnings: Boolean = true,
  val registryDebug: Boolean = false,
)

data class InstrumentationScopeConfig(
  val enabled: Boolean? = null,
  val counters: Boolean? = null,
  val timers: Boolean? = null,
)

data class InstrumentationConfig(
  val enabled: Boolean = false,
  val counters: Boolean = false,
  val timers: Boolean = false,
  val scopes: Map<TerrasectInstrScope, InstrumentationScopeConfig> = emptyMap(),
  val events: Map<TerrasectMetricEvent, Boolean> = emptyMap(),
)

data class TerrasectConfig(
  val preset: String? = null,
  val logging: LoggingConfig = LoggingConfig(),
  val instrumentation: InstrumentationConfig = InstrumentationConfig(),
)

object ConfigLogging {
  @Volatile var registryDebug: Boolean = false
}
