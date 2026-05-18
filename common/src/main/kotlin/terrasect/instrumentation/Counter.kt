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
internal class ManagedCounter(private val scope: InstrScope, private val delegate: InstrCounter) :
  InstrCounter {
  override val id: MetricId
    get() = delegate.id

  override fun increment(delta: Long) {
    if (MetricsConfig.isCounterEnabled(scope)) delegate.increment(delta)
  }

  override fun snapshot() = delegate.snapshot()

  override fun snapshotAndReset() = delegate.snapshotAndReset()

  override fun reset() = delegate.reset()
}
