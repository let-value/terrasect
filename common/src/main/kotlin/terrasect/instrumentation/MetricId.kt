package terrasect.instrumentation

/** Stable scope identity for a subsystem that owns metrics, similar to a logger name. */
interface InstrScope {
  val id: String
}

/** Stable event identity for a metric. Prefer enums or sealed objects over ad-hoc strings. */
interface MetricEvent {
  val id: String
}

data object RootInstrScope : InstrScope {
  override val id: String = "terrasect"
}

/**
 * Low-cardinality metric tag.
 *
 * Good values are stable categories such as dimension, result, reason, mob, structure, biome,
 * packet_type, or phase. Avoid positions, UUIDs, raw exception messages, NBT dumps, serialized item
 * stacks, random seeds, and other high-cardinality values.
 */
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
