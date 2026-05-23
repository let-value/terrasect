package terrasect.instrumentation

import java.util.concurrent.atomic.LongAdder

interface InstrCounter {
  val id: MetricId

  fun increment(delta: Long = 1)

  fun snapshot(): CounterSnapshot

  fun snapshotAndReset(): CounterSnapshot

  fun reset()
}

@PublishedApi
internal class NoOpCounter(override val id: MetricId) : InstrCounter {
  override fun increment(delta: Long) = Unit

  override fun snapshot() = CounterSnapshot(id, 0L)

  override fun snapshotAndReset() = CounterSnapshot(id, 0L)

  override fun reset() = Unit
}

internal class InMemoryCounter(override val id: MetricId) : InstrCounter {
  private val value = LongAdder()

  override fun increment(delta: Long) {
    value.add(delta)
  }

  override fun snapshot() = CounterSnapshot(id, value.sum())

  override fun snapshotAndReset() = CounterSnapshot(id, value.sumThenReset())

  override fun reset() {
    value.reset()
  }
}

@PublishedApi
internal class ManagedCounter(
  private val scope: InstrScope,
  private val idFactory: MetricIdFactory,
) : InstrCounter {
  override val id: MetricId
    get() = idFactory.metricId()

  override fun increment(delta: Long) {
    if (MetricsConfig.isCounterEnabled(scope)) {
      Instr.currentBackend().counter(idFactory.metricId()).increment(delta)
    }
  }

  override fun snapshot(): CounterSnapshot =
    Instr.currentBackend().counter(idFactory.metricId()).snapshot()

  override fun snapshotAndReset(): CounterSnapshot =
    Instr.currentBackend().counter(idFactory.metricId()).snapshotAndReset()

  override fun reset() {
    Instr.currentBackend().counter(idFactory.metricId()).reset()
  }
}
