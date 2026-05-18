package terrasect.instrumentation

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private enum class TestScope(override val id: String) : InstrScope {
  WORLDGEN("worldgen"),
  NETWORK("network"),
}

private enum class TestEvent(override val id: String) : MetricEvent {
  ATTEMPT("worldgen.attempt"),
  TIMER("worldgen.phase"),
  PACKET("packet.handle"),
}

class MetricsTest {
  private lateinit var backend: InMemoryBackend
  private lateinit var worldgen: ScopedInstr

  @BeforeEach
  fun setUp() {
    MetricsConfig.enabled = true
    MetricsConfig.countersEnabled = true
    MetricsConfig.timersEnabled = true
    MetricsConfig.clearScopeOverrides()
    backend = InMemoryBackend()
    Instr.setBackend(backend)
    worldgen = Instr.scoped(TestScope.WORLDGEN)
  }

  @AfterEach
  fun tearDown() {
    MetricsConfig.enabled = false
    MetricsConfig.countersEnabled = false
    MetricsConfig.timersEnabled = false
    MetricsConfig.clearScopeOverrides()
    Instr.setBackend(InMemoryBackend())
  }

  @Test
  fun `disabled counters do not record and lazy tags are not evaluated`() {
    MetricsConfig.countersEnabled = false
    var evaluated = false
    worldgen.count(TestEvent.ATTEMPT, "dimension") {
      evaluated = true
      "overworld"
    }

    assertFalse(evaluated)
    assertTrue(Instr.counterSnapshot().isEmpty())
  }

  @Test
  fun `disabled timers execute block without recording and lazy tags are not evaluated`() {
    MetricsConfig.timersEnabled = false
    var evaluated = false
    val result =
      worldgen.time(
        TestEvent.TIMER,
        "phase",
        {
          evaluated = true
          "decorate"
        },
      ) {
        "ok"
      }

    assertEquals("ok", result)
    assertFalse(evaluated)
    assertTrue(Instr.timerSnapshot().isEmpty())
  }

  @Test
  fun `enabled counters increment with scoped identity`() {
    worldgen.count(TestEvent.ATTEMPT)
    worldgen.count(TestEvent.ATTEMPT)

    assertEquals(
      listOf(CounterSnapshot(MetricId("worldgen", "worldgen.attempt"), 2L)),
      Instr.counterSnapshot(),
    )
  }

  @Test
  fun `enabled counters with tags are stored separately`() {
    worldgen.count(TestEvent.ATTEMPT, "dimension") { "overworld" }
    worldgen.count(TestEvent.ATTEMPT, "dimension") { "nether" }
    worldgen.count(TestEvent.ATTEMPT, "dimension") { "overworld" }

    assertEquals(
      listOf(
        CounterSnapshot(
          MetricId("worldgen", "worldgen.attempt", listOf(MetricTag("dimension", "nether"))),
          1L,
        ),
        CounterSnapshot(
          MetricId("worldgen", "worldgen.attempt", listOf(MetricTag("dimension", "overworld"))),
          2L,
        ),
      ),
      Instr.counterSnapshot(),
    )
  }

  @Test
  fun `two and three tag overloads preserve tag order in stable snapshots`() {
    worldgen.count(TestEvent.ATTEMPT, "dimension", { "overworld" }, "result", { "placed" })
    worldgen.count(
      TestEvent.ATTEMPT,
      "dimension",
      { "overworld" },
      "result",
      { "skipped" },
      "reason",
      { "biome" },
    )

    assertEquals(
      listOf(
        CounterSnapshot(
          MetricId(
            "worldgen",
            "worldgen.attempt",
            listOf(MetricTag("dimension", "overworld"), MetricTag("result", "placed")),
          ),
          1L,
        ),
        CounterSnapshot(
          MetricId(
            "worldgen",
            "worldgen.attempt",
            listOf(
              MetricTag("dimension", "overworld"),
              MetricTag("result", "skipped"),
              MetricTag("reason", "biome"),
            ),
          ),
          1L,
        ),
      ),
      Instr.counterSnapshot(),
    )
  }

  @Test
  fun `scope gate disables only matching scope`() {
    val network = Instr.scoped(TestScope.NETWORK)
    MetricsConfig.setScopeCountersEnabled(TestScope.WORLDGEN, false)

    worldgen.count(TestEvent.ATTEMPT)
    network.count(TestEvent.PACKET)

    assertEquals(
      listOf(CounterSnapshot(MetricId("network", "packet.handle"), 1L)),
      Instr.counterSnapshot(),
    )
  }

  @Test
  fun `bound counter handle observes scope gates`() {
    val counter = worldgen.counter(TestEvent.ATTEMPT)
    counter.increment()
    MetricsConfig.setScopeEnabled(TestScope.WORLDGEN, false)
    counter.increment()

    assertEquals(CounterSnapshot(MetricId("worldgen", "worldgen.attempt"), 1L), counter.snapshot())
  }

  @Test
  fun `timer records count total average and max`() {
    val timer = worldgen.timer(TestEvent.TIMER)
    timer.recordDurationNanos(100L)
    timer.recordDurationNanos(300L)

    assertEquals(
      TimerSnapshot(MetricId("worldgen", "worldgen.phase"), 2L, 400L, 300L),
      timer.snapshot(),
    )
    assertEquals(200L, timer.snapshot().averageNanos)
  }

  @Test
  fun `timer wrapped block returns original value`() {
    assertEquals(42, worldgen.time(TestEvent.TIMER) { 42 })
    assertEquals(1L, Instr.timerSnapshot().single().count)
  }

  @Test
  fun `timer wrapped block propagates exceptions and still records`() {
    val thrown =
      assertThrows<IllegalStateException> {
        worldgen.time(TestEvent.TIMER) { throw IllegalStateException("boom") }
      }

    assertEquals("boom", thrown.message)
    assertEquals(1L, Instr.timerSnapshot().single().count)
  }

  @Test
  fun `manual timing guard skips nanos when disabled`() {
    val timer = worldgen.timer(TestEvent.TIMER)
    MetricsConfig.timersEnabled = false
    if (timer.isTimingEnabled) {
      timer.recordDurationNanos(999L)
    }

    assertEquals(0L, timer.snapshot().count)
  }

  @Test
  fun `snapshot and reset returns stable data and clears`() {
    worldgen.count(TestEvent.ATTEMPT)
    worldgen.timer(TestEvent.TIMER).recordDurationNanos(50L)

    assertEquals(1L, Instr.counterSnapshotAndReset().single().value)
    assertEquals(1L, Instr.timerSnapshotAndReset().single().count)
    assertEquals(0L, Instr.counterSnapshot().single().value)
    assertEquals(0L, Instr.timerSnapshot().single().count)
  }

  @Test
  fun `noop backend snapshots are empty and timed blocks still run`() {
    Instr.setBackend(NoOpBackend)
    var ran = false
    val result =
      worldgen.time(TestEvent.TIMER) {
        ran = true
        "done"
      }
    worldgen.count(TestEvent.ATTEMPT)

    assertTrue(ran)
    assertEquals("done", result)
    assertTrue(Instr.counterSnapshot().isEmpty())
    assertTrue(Instr.timerSnapshot().isEmpty())
  }
}
