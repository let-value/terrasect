package terrasect.instrumentation

import java.util.concurrent.ConcurrentHashMap

interface MetricsBackend {
  fun counter(id: MetricId): InstrCounter

  fun timer(id: MetricId): InstrTimer

  fun counterSnapshots(): List<CounterSnapshot>

  fun timerSnapshots(): List<TimerSnapshot>

  fun counterSnapshotsAndReset(): List<CounterSnapshot>

  fun timerSnapshotsAndReset(): List<TimerSnapshot>

  fun reset()
}

object NoOpBackend : MetricsBackend {
  override fun counter(id: MetricId): InstrCounter = NoOpCounter(id)

  override fun timer(id: MetricId): InstrTimer = NoOpTimer(id)

  override fun counterSnapshots(): List<CounterSnapshot> = emptyList()

  override fun timerSnapshots(): List<TimerSnapshot> = emptyList()

  override fun counterSnapshotsAndReset(): List<CounterSnapshot> = emptyList()

  override fun timerSnapshotsAndReset(): List<TimerSnapshot> = emptyList()

  override fun reset() = Unit
}

class InMemoryBackend : MetricsBackend {
  private val counters = ConcurrentHashMap<MetricId, InMemoryCounter>()
  private val timers = ConcurrentHashMap<MetricId, InMemoryTimer>()

  override fun counter(id: MetricId): InstrCounter =
    counters.computeIfAbsent(id) { InMemoryCounter(it) }

  override fun timer(id: MetricId): InstrTimer = timers.computeIfAbsent(id) { InMemoryTimer(it) }

  override fun counterSnapshots(): List<CounterSnapshot> =
    counters.values.map { it.snapshot() }.sortedBy { it.id.stableSortKey() }

  override fun timerSnapshots(): List<TimerSnapshot> =
    timers.values.map { it.snapshot() }.sortedBy { it.id.stableSortKey() }

  override fun counterSnapshotsAndReset(): List<CounterSnapshot> =
    counters.values.map { it.snapshotAndReset() }.sortedBy { it.id.stableSortKey() }

  override fun timerSnapshotsAndReset(): List<TimerSnapshot> =
    timers.values.map { it.snapshotAndReset() }.sortedBy { it.id.stableSortKey() }

  override fun reset() {
    counters.values.forEach { it.reset() }
    timers.values.forEach { it.reset() }
  }

  fun counterSnapshot(id: MetricId): CounterSnapshot? = counters[id]?.snapshot()

  fun timerSnapshot(id: MetricId): TimerSnapshot? = timers[id]?.snapshot()
}

private fun MetricId.stableSortKey(): String = buildString {
  append(scope)
  append('\u0000')
  append(event)
  for (tag in tags) {
    append('\u0000')
    append(tag.key)
    append('=')
    append(tag.value)
  }
}
