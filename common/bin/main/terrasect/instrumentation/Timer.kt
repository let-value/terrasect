package terrasect.instrumentation

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

interface InstrTimer {
  val id: MetricId
  val isTimingEnabled: Boolean

  fun <T> time(block: () -> T): T

  fun recordDurationNanos(nanos: Long)

  fun snapshot(): TimerSnapshot

  fun snapshotAndReset(): TimerSnapshot

  fun reset()
}

@PublishedApi
internal class NoOpTimer(override val id: MetricId) : InstrTimer {
  override val isTimingEnabled: Boolean
    get() = false

  override fun <T> time(block: () -> T): T = block()

  override fun recordDurationNanos(nanos: Long) = Unit

  override fun snapshot() = TimerSnapshot(id, 0L, 0L, 0L)

  override fun snapshotAndReset() = TimerSnapshot(id, 0L, 0L, 0L)

  override fun reset() = Unit
}

internal class InMemoryTimer(override val id: MetricId) : InstrTimer {
  private val count = LongAdder()
  private val totalNanos = LongAdder()
  private val maxNanos = AtomicLong(0L)

  override val isTimingEnabled: Boolean
    get() = true

  override fun <T> time(block: () -> T): T {
    val start = System.nanoTime()
    try {
      return block()
    } finally {
      recordDurationNanos(System.nanoTime() - start)
    }
  }

  override fun recordDurationNanos(nanos: Long) {
    count.increment()
    totalNanos.add(nanos)
    maxNanos.accumulateAndGet(nanos, ::maxOf)
  }

  override fun snapshot() = TimerSnapshot(id, count.sum(), totalNanos.sum(), maxNanos.get())

  override fun snapshotAndReset(): TimerSnapshot {
    val c = count.sumThenReset()
    val total = totalNanos.sumThenReset()
    val max = maxNanos.getAndSet(0L)
    return TimerSnapshot(id, c, total, max)
  }

  override fun reset() {
    count.reset()
    totalNanos.reset()
    maxNanos.set(0L)
  }
}

@PublishedApi
internal class ManagedTimer(private val scope: InstrScope, private val idFactory: MetricIdFactory) :
  InstrTimer {
  override val id: MetricId
    get() = idFactory.metricId()

  override val isTimingEnabled: Boolean
    get() = MetricsConfig.isTimerEnabled(scope)

  override fun <T> time(block: () -> T): T {
    if (!MetricsConfig.isTimerEnabled(scope)) return block()
    val timer = Instr.currentBackend().timer(idFactory.metricId())
    val start = System.nanoTime()
    try {
      return block()
    } finally {
      timer.recordDurationNanos(System.nanoTime() - start)
    }
  }

  override fun recordDurationNanos(nanos: Long) {
    if (MetricsConfig.isTimerEnabled(scope)) {
      Instr.currentBackend().timer(idFactory.metricId()).recordDurationNanos(nanos)
    }
  }

  override fun snapshot(): TimerSnapshot =
    Instr.currentBackend().timer(idFactory.metricId()).snapshot()

  override fun snapshotAndReset(): TimerSnapshot =
    Instr.currentBackend().timer(idFactory.metricId()).snapshotAndReset()

  override fun reset() {
    Instr.currentBackend().timer(idFactory.metricId()).reset()
  }
}
