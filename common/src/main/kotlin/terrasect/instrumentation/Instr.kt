package terrasect.instrumentation

object Instr {
  @Volatile private var backend: MetricsBackend = InMemoryBackend()

  @PublishedApi internal fun currentBackend(): MetricsBackend = backend

  val root: ScopedInstr = ScopedInstr(RootInstrScope)

  fun scoped(scope: InstrScope): ScopedInstr = ScopedInstr(scope)

  fun setBackend(backend: MetricsBackend) {
    this.backend = backend
  }

  fun getBackend(): MetricsBackend = backend

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

  inline fun counter(event: MetricEvent): InstrCounter = root.counter(event)

  inline fun counter(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
  ): InstrCounter = root.counter(event, key0, value0)

  inline fun counter(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
  ): InstrCounter = root.counter(event, key0, value0, key1, value1)

  inline fun counter(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
    key2: String,
    crossinline value2: () -> String,
  ): InstrCounter = root.counter(event, key0, value0, key1, value1, key2, value2)

  inline fun timer(event: MetricEvent): InstrTimer = root.timer(event)

  inline fun timer(event: MetricEvent, key0: String, crossinline value0: () -> String): InstrTimer =
    root.timer(event, key0, value0)

  inline fun timer(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
  ): InstrTimer = root.timer(event, key0, value0, key1, value1)

  inline fun timer(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
    key2: String,
    crossinline value2: () -> String,
  ): InstrTimer = root.timer(event, key0, value0, key1, value1, key2, value2)
}

class ScopedInstr(val scope: InstrScope) {
  inline fun count(event: MetricEvent, delta: Long = 1) {
    if (!MetricsConfig.isCounterEnabled(scope)) return
    Instr.currentBackend().counter(MetricId(scope.id, event.id)).increment(delta)
  }

  inline fun count(event: MetricEvent, key0: String, crossinline value0: () -> String) {
    if (!MetricsConfig.isCounterEnabled(scope)) return
    Instr.currentBackend()
      .counter(MetricId(scope.id, event.id, listOf(MetricTag(key0, value0()))))
      .increment()
  }

  inline fun count(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
  ) {
    if (!MetricsConfig.isCounterEnabled(scope)) return
    Instr.currentBackend()
      .counter(
        MetricId(scope.id, event.id, listOf(MetricTag(key0, value0()), MetricTag(key1, value1())))
      )
      .increment()
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
    if (!MetricsConfig.isCounterEnabled(scope)) return
    Instr.currentBackend()
      .counter(
        MetricId(
          scope.id,
          event.id,
          listOf(MetricTag(key0, value0()), MetricTag(key1, value1()), MetricTag(key2, value2())),
        )
      )
      .increment()
  }

  inline fun <T> time(event: MetricEvent, block: () -> T): T {
    if (!MetricsConfig.isTimerEnabled(scope)) return block()
    val timer = Instr.currentBackend().timer(MetricId(scope.id, event.id))
    val start = System.nanoTime()
    try {
      return block()
    } finally {
      timer.recordDurationNanos(System.nanoTime() - start)
    }
  }

  inline fun <T> time(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    block: () -> T,
  ): T {
    if (!MetricsConfig.isTimerEnabled(scope)) return block()
    val timer =
      Instr.currentBackend().timer(MetricId(scope.id, event.id, listOf(MetricTag(key0, value0()))))
    val start = System.nanoTime()
    try {
      return block()
    } finally {
      timer.recordDurationNanos(System.nanoTime() - start)
    }
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
    val timer =
      Instr.currentBackend()
        .timer(
          MetricId(scope.id, event.id, listOf(MetricTag(key0, value0()), MetricTag(key1, value1())))
        )
    val start = System.nanoTime()
    try {
      return block()
    } finally {
      timer.recordDurationNanos(System.nanoTime() - start)
    }
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
    val timer =
      Instr.currentBackend()
        .timer(
          MetricId(
            scope.id,
            event.id,
            listOf(MetricTag(key0, value0()), MetricTag(key1, value1()), MetricTag(key2, value2())),
          )
        )
    val start = System.nanoTime()
    try {
      return block()
    } finally {
      timer.recordDurationNanos(System.nanoTime() - start)
    }
  }

  inline fun counter(event: MetricEvent): InstrCounter =
    ManagedCounter(scope, Instr.currentBackend().counter(MetricId(scope.id, event.id)))

  inline fun counter(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
  ): InstrCounter {
    if (!MetricsConfig.isCounterEnabled(scope)) {
      return ManagedCounter(
        scope,
        Instr.currentBackend().counter(MetricId(scope.id, event.id, emptyList())),
      )
    }
    return ManagedCounter(
      scope,
      Instr.currentBackend()
        .counter(MetricId(scope.id, event.id, listOf(MetricTag(key0, value0())))),
    )
  }

  inline fun counter(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
  ): InstrCounter {
    if (!MetricsConfig.isCounterEnabled(scope)) {
      return ManagedCounter(
        scope,
        Instr.currentBackend().counter(MetricId(scope.id, event.id, emptyList())),
      )
    }
    return ManagedCounter(
      scope,
      Instr.currentBackend()
        .counter(
          MetricId(scope.id, event.id, listOf(MetricTag(key0, value0()), MetricTag(key1, value1())))
        ),
    )
  }

  inline fun counter(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
    key2: String,
    crossinline value2: () -> String,
  ): InstrCounter {
    if (!MetricsConfig.isCounterEnabled(scope)) {
      return ManagedCounter(
        scope,
        Instr.currentBackend().counter(MetricId(scope.id, event.id, emptyList())),
      )
    }
    return ManagedCounter(
      scope,
      Instr.currentBackend()
        .counter(
          MetricId(
            scope.id,
            event.id,
            listOf(MetricTag(key0, value0()), MetricTag(key1, value1()), MetricTag(key2, value2())),
          )
        ),
    )
  }

  inline fun timer(event: MetricEvent): InstrTimer =
    ManagedTimer(scope, Instr.currentBackend().timer(MetricId(scope.id, event.id)))

  inline fun timer(event: MetricEvent, key0: String, crossinline value0: () -> String): InstrTimer {
    if (!MetricsConfig.isTimerEnabled(scope)) {
      return ManagedTimer(
        scope,
        Instr.currentBackend().timer(MetricId(scope.id, event.id, emptyList())),
      )
    }
    return ManagedTimer(
      scope,
      Instr.currentBackend().timer(MetricId(scope.id, event.id, listOf(MetricTag(key0, value0())))),
    )
  }

  inline fun timer(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
  ): InstrTimer {
    if (!MetricsConfig.isTimerEnabled(scope)) {
      return ManagedTimer(
        scope,
        Instr.currentBackend().timer(MetricId(scope.id, event.id, emptyList())),
      )
    }
    return ManagedTimer(
      scope,
      Instr.currentBackend()
        .timer(
          MetricId(scope.id, event.id, listOf(MetricTag(key0, value0()), MetricTag(key1, value1())))
        ),
    )
  }

  inline fun timer(
    event: MetricEvent,
    key0: String,
    crossinline value0: () -> String,
    key1: String,
    crossinline value1: () -> String,
    key2: String,
    crossinline value2: () -> String,
  ): InstrTimer {
    if (!MetricsConfig.isTimerEnabled(scope)) {
      return ManagedTimer(
        scope,
        Instr.currentBackend().timer(MetricId(scope.id, event.id, emptyList())),
      )
    }
    return ManagedTimer(
      scope,
      Instr.currentBackend()
        .timer(
          MetricId(
            scope.id,
            event.id,
            listOf(MetricTag(key0, value0()), MetricTag(key1, value1()), MetricTag(key2, value2())),
          )
        ),
    )
  }
}
