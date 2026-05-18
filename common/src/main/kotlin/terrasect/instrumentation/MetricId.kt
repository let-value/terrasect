package terrasect.instrumentation

interface InstrScope {
  val id: String
}

interface MetricEvent {
  val id: String
}

data object RootInstrScope : InstrScope {
  override val id: String = "terrasect"
}

data class MetricTag(val key: String, val value: String)

data class MetricId(val scope: String, val event: String, val tags: List<MetricTag> = emptyList())

data class CounterSnapshot(val id: MetricId, val value: Long)

data class TimerSnapshot(
  val id: MetricId,
  val count: Long,
  val totalNanos: Long,
  val maxNanos: Long,
) {
  val averageNanos: Long
    get() = if (count == 0L) 0L else totalNanos / count
}
