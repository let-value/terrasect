package terrasect.instrumentation

@Suppress("NOTHING_TO_INLINE")
object Instr {
  @Volatile private var backend: MetricsBackend = InMemoryBackend()

  @PublishedApi internal fun currentBackend(): MetricsBackend = backend

  val root: ScopedInstr = ScopedInstr(RootInstrScope)

  fun scoped(scope: InstrScope): ScopedInstr = ScopedInstr(scope)

  fun setBackend(backend: MetricsBackend) {
    this.backend = backend
  }

  fun getBackend(): MetricsBackend = backend

  fun isCounterEnabled(): Boolean = root.isCounterEnabled

  fun isTimingEnabled(): Boolean = root.isTimingEnabled

  fun counterSnapshot(): List<CounterSnapshot> = backend.counterSnapshots()

  fun timerSnapshot(): List<TimerSnapshot> = backend.timerSnapshots()

  fun counterSnapshotAndReset(): List<CounterSnapshot> = backend.counterSnapshotsAndReset()

  fun timerSnapshotAndReset(): List<TimerSnapshot> = backend.timerSnapshotsAndReset()

  fun reset() = backend.reset()

  inline fun count(event: MetricEvent, delta: Long = 1) = root.count(event, delta)

  inline fun count(event: MetricEvent, key0: String, crossinline value0: () -> String) =
    root.count(event, key0, value0)

  inline fun count(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
  ) = root.count(event, key0, value0, key1, value1)

  inline fun count(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
    key2: String,
    crossinline value2: () -> String,
  ) = root.count(event, key0, value0, key1, value1, key2, value2)

  inline fun <T> time(event: MetricEvent, block: () -> T): T = root.time(event, block)

  inline fun <T> time(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    block: () -> T,
  ): T = root.time(event, key0, value0, block)

  inline fun <T> time(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
    block: () -> T,
  ): T = root.time(event, key0, value0, key1, value1, block)

  inline fun <T> time(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
    key2: String,
    crossinline value2: () -> String,
    block: () -> T,
  ): T = root.time(event, key0, value0, key1, value1, key2, value2, block)

  inline fun recordDurationNanos(event: MetricEvent, nanos: Long) =
    root.recordDurationNanos(event, nanos)

  inline fun recordDurationNanos(
    event: MetricEvent,
    nanos: Long,
    key0: String,
    crossinline value0: () -> String,
  ) = root.recordDurationNanos(event, nanos, key0, value0)

  inline fun recordDurationNanos(
    event: MetricEvent,
    nanos: Long,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
  ) = root.recordDurationNanos(event, nanos, key0, value0, key1, value1)

  inline fun recordDurationNanos(
    event: MetricEvent,
    nanos: Long,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
    key2: String,
    crossinline value2: () -> String,
  ) = root.recordDurationNanos(event, nanos, key0, value0, key1, value1, key2, value2)

  inline fun counter(event: MetricEvent): InstrCounter = root.counter(event)

  inline fun counter(
    event: MetricEvent,
    key0: String,
    noinline value0: () -> String,
  ): InstrCounter = root.counter(event, key0, value0)

  inline fun counter(
    event: MetricEvent,
    key0: String,
    noinline value0: () -> String,
    key1: String,
    noinline value1: () -> String,
  ): InstrCounter = root.counter(event, key0, value0, key1, value1)

  inline fun counter(
    event: MetricEvent,
    key0: String,
    noinline value0: () -> String,
    key1: String,
    noinline value1: () -> String,
    key2: String,
    noinline value2: () -> String,
  ): InstrCounter = root.counter(event, key0, value0, key1, value1, key2, value2)

  inline fun timer(event: MetricEvent): InstrTimer = root.timer(event)

  inline fun timer(event: MetricEvent, key0: String, noinline value0: () -> String): InstrTimer =
    root.timer(event, key0, value0)

  inline fun timer(
    event: MetricEvent,
    key0: String,
    noinline value0: () -> String,
    key1: String,
    noinline value1: () -> String,
  ): InstrTimer = root.timer(event, key0, value0, key1, value1)

  inline fun timer(
    event: MetricEvent,
    key0: String,
    noinline value0: () -> String,
    key1: String,
    noinline value1: () -> String,
    key2: String,
    noinline value2: () -> String,
  ): InstrTimer = root.timer(event, key0, value0, key1, value1, key2, value2)
}

@Suppress("NOTHING_TO_INLINE")
class ScopedInstr(val scope: InstrScope) {
  val isCounterEnabled: Boolean
    get() = MetricsConfig.isCounterEnabled(scope)

  val isTimingEnabled: Boolean
    get() = MetricsConfig.isTimerEnabled(scope)

  inline fun count(event: MetricEvent, delta: Long = 1) {
    if (!MetricsConfig.isCounterEnabled(scope, event)) return
    Instr.currentBackend().counter(MetricId(scope.id, event.id)).increment(delta)
  }

  inline fun count(event: MetricEvent, key0: String, crossinline value0: () -> String) {
    if (!MetricsConfig.isCounterEnabled(scope, event)) return
    Instr.currentBackend().counter(metricId(event, key0, value0())).increment()
  }

  inline fun count(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
  ) {
    if (!MetricsConfig.isCounterEnabled(scope, event)) return
    Instr.currentBackend().counter(metricId(event, key0, value0(), key1, value1())).increment()
  }

  inline fun count(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
    key2: String,
    crossinline value2: () -> String,
  ) {
    if (!MetricsConfig.isCounterEnabled(scope, event)) return
    Instr.currentBackend()
      .counter(metricId(event, key0, value0(), key1, value1(), key2, value2()))
      .increment()
  }

  inline fun <T> time(event: MetricEvent, block: () -> T): T {
    if (!MetricsConfig.isTimerEnabled(scope)) return block()
    return timeEnabled(MetricId(scope.id, event.id), block)
  }

  inline fun <T> time(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    block: () -> T,
  ): T {
    if (!MetricsConfig.isTimerEnabled(scope)) return block()
    return timeEnabled(metricId(event, key0, value0()), block)
  }

  inline fun <T> time(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
    block: () -> T,
  ): T {
    if (!MetricsConfig.isTimerEnabled(scope)) return block()
    return timeEnabled(metricId(event, key0, value0(), key1, value1()), block)
  }

  inline fun <T> time(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
    key2: String,
    crossinline value2: () -> String,
    block: () -> T,
  ): T {
    if (!MetricsConfig.isTimerEnabled(scope)) return block()
    return timeEnabled(metricId(event, key0, value0(), key1, value1(), key2, value2()), block)
  }

  inline fun recordDurationNanos(event: MetricEvent, nanos: Long) {
    if (!MetricsConfig.isTimerEnabled(scope)) return
    Instr.currentBackend().timer(MetricId(scope.id, event.id)).recordDurationNanos(nanos)
  }

  inline fun recordDurationNanos(
    event: MetricEvent,
    nanos: Long,
    key0: String,
    crossinline value0: () -> String,
  ) {
    if (!MetricsConfig.isTimerEnabled(scope)) return
    Instr.currentBackend().timer(metricId(event, key0, value0())).recordDurationNanos(nanos)
  }

  inline fun recordDurationNanos(
    event: MetricEvent,
    nanos: Long,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
  ) {
    if (!MetricsConfig.isTimerEnabled(scope)) return
    Instr.currentBackend()
      .timer(metricId(event, key0, value0(), key1, value1()))
      .recordDurationNanos(nanos)
  }

  inline fun recordDurationNanos(
    event: MetricEvent,
    nanos: Long,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
    key2: String,
    crossinline value2: () -> String,
  ) {
    if (!MetricsConfig.isTimerEnabled(scope)) return
    Instr.currentBackend()
      .timer(metricId(event, key0, value0(), key1, value1(), key2, value2()))
      .recordDurationNanos(nanos)
  }

  fun counter(event: MetricEvent): InstrCounter =
    ManagedCounter(scope, FixedMetricId(MetricId(scope.id, event.id)))

  fun counter(event: MetricEvent, key0: String, value0: () -> String): InstrCounter =
    ManagedCounter(scope, OneTagMetricId(scope, event, key0, value0))

  fun counter(
    event: MetricEvent,
    key0: String,
    value0: () -> String,
    key1: String,
    value1: () -> String,
  ): InstrCounter = ManagedCounter(scope, TwoTagMetricId(scope, event, key0, value0, key1, value1))

  fun counter(
    event: MetricEvent,
    key0: String,
    value0: () -> String,
    key1: String,
    value1: () -> String,
    key2: String,
    value2: () -> String,
  ): InstrCounter =
    ManagedCounter(scope, ThreeTagMetricId(scope, event, key0, value0, key1, value1, key2, value2))

  fun timer(event: MetricEvent): InstrTimer =
    ManagedTimer(scope, FixedMetricId(MetricId(scope.id, event.id)))

  fun timer(event: MetricEvent, key0: String, value0: () -> String): InstrTimer =
    ManagedTimer(scope, OneTagMetricId(scope, event, key0, value0))

  fun timer(
    event: MetricEvent,
    key0: String,
    value0: () -> String,
    key1: String,
    value1: () -> String,
  ): InstrTimer = ManagedTimer(scope, TwoTagMetricId(scope, event, key0, value0, key1, value1))

  fun timer(
    event: MetricEvent,
    key0: String,
    value0: () -> String,
    key1: String,
    value1: () -> String,
    key2: String,
    value2: () -> String,
  ): InstrTimer =
    ManagedTimer(scope, ThreeTagMetricId(scope, event, key0, value0, key1, value1, key2, value2))

  @PublishedApi
  internal fun metricId(event: MetricEvent, key0: String, value0: String): MetricId =
    MetricId(scope.id, event.id, listOf(MetricTag(key0, value0)))

  @PublishedApi
  internal fun metricId(
    event: MetricEvent,
    key0: String,
    value0: String,
    key1: String,
    value1: String,
  ): MetricId =
    MetricId(scope.id, event.id, listOf(MetricTag(key0, value0), MetricTag(key1, value1)))

  @PublishedApi
  internal fun metricId(
    event: MetricEvent,
    key0: String,
    value0: String,
    key1: String,
    value1: String,
    key2: String,
    value2: String,
  ): MetricId =
    MetricId(
      scope.id,
      event.id,
      listOf(MetricTag(key0, value0), MetricTag(key1, value1), MetricTag(key2, value2)),
    )

  @PublishedApi
  internal inline fun <T> timeEnabled(id: MetricId, block: () -> T): T {
    val timer = Instr.currentBackend().timer(id)
    val start = System.nanoTime()
    try {
      return block()
    } finally {
      timer.recordDurationNanos(System.nanoTime() - start)
    }
  }
}

internal interface MetricIdFactory {
  fun metricId(): MetricId
}

private class FixedMetricId(private val id: MetricId) : MetricIdFactory {
  override fun metricId(): MetricId = id
}

private class OneTagMetricId(
  private val scope: InstrScope,
  private val event: MetricEvent,
  private val key0: String,
  private val value0: () -> String,
) : MetricIdFactory {
  override fun metricId(): MetricId =
    MetricId(scope.id, event.id, listOf(MetricTag(key0, value0())))
}

private class TwoTagMetricId(
  private val scope: InstrScope,
  private val event: MetricEvent,
  private val key0: String,
  private val value0: () -> String,
  private val key1: String,
  private val value1: () -> String,
) : MetricIdFactory {
  override fun metricId(): MetricId =
    MetricId(scope.id, event.id, listOf(MetricTag(key0, value0()), MetricTag(key1, value1())))
}

private class ThreeTagMetricId(
  private val scope: InstrScope,
  private val event: MetricEvent,
  private val key0: String,
  private val value0: () -> String,
  private val key1: String,
  private val value1: () -> String,
  private val key2: String,
  private val value2: () -> String,
) : MetricIdFactory {
  override fun metricId(): MetricId =
    MetricId(
      scope.id,
      event.id,
      listOf(MetricTag(key0, value0()), MetricTag(key1, value1()), MetricTag(key2, value2())),
    )
}
