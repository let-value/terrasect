package terrasect.instrumentation

import java.util.concurrent.ConcurrentHashMap

object MetricsConfig {
  @Volatile var enabled: Boolean = false
  @Volatile var countersEnabled: Boolean = false
  @Volatile var timersEnabled: Boolean = false

  private val scopeEnabled = ConcurrentHashMap<String, Boolean>()
  private val scopeCountersEnabled = ConcurrentHashMap<String, Boolean>()
  private val scopeTimersEnabled = ConcurrentHashMap<String, Boolean>()
  private val eventCountersEnabled = ConcurrentHashMap<String, Boolean>()

  fun setScopeEnabled(scope: InstrScope, enabled: Boolean?) = set(scopeEnabled, scope.id, enabled)

  fun setScopeCountersEnabled(scope: InstrScope, enabled: Boolean?) =
    set(scopeCountersEnabled, scope.id, enabled)

  fun setScopeTimersEnabled(scope: InstrScope, enabled: Boolean?) =
    set(scopeTimersEnabled, scope.id, enabled)

  fun setEventCountersEnabled(event: MetricEvent, enabled: Boolean?) =
    set(eventCountersEnabled, event.id, enabled)

  fun clearScopeOverrides() {
    scopeEnabled.clear()
    scopeCountersEnabled.clear()
    scopeTimersEnabled.clear()
    eventCountersEnabled.clear()
  }

  @PublishedApi
  internal fun isScopeEnabled(scope: InstrScope): Boolean =
    enabled && scopeEnabled[scope.id] != false

  @PublishedApi
  internal fun isCounterEnabled(scope: InstrScope): Boolean =
    enabled &&
      countersEnabled &&
      scopeEnabled[scope.id] != false &&
      scopeCountersEnabled[scope.id] != false

  @PublishedApi
  internal fun isCounterEnabled(scope: InstrScope, event: MetricEvent): Boolean =
    isCounterEnabled(scope) && eventCountersEnabled[event.id] != false

  @PublishedApi
  internal fun isTimerEnabled(scope: InstrScope): Boolean =
    enabled &&
      timersEnabled &&
      scopeEnabled[scope.id] != false &&
      scopeTimersEnabled[scope.id] != false

  private fun set(map: ConcurrentHashMap<String, Boolean>, key: String, enabled: Boolean?) {
    if (enabled == null) map.remove(key) else map[key] = enabled
  }
}
