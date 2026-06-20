package terrasect.instrumentation

enum class TerrasectInstrScope(override val id: String) : InstrScope {
  STRUCTURE("structure"),
  CLIMATE("climate"),
  NOISE("noise"),
  CHUNK("chunk"),
  TRAVERSAL("traversal"),
  LOOT("loot"),
}

enum class TerrasectMetricEvent(override val id: String) : MetricEvent {
  STRUCTURE_APPLIED("structure.applied"),
  STRUCTURE_CHUNK_MISSING("structure.chunk_missing"),
  STRUCTURE_GENERATED("structure.generated"),
  CLIMATE_APPLIED("climate.applied"),
  CLIMATE_CHUNK_MISSING("climate.chunk_missing"),
  NOISE_ROUTER_WRAP("noise.router.wrap"),
  NOISE_FUNCTION_WRAP("noise.function.wrap"),
  NOISE_APPLIED("noise.applied"),
  NOISE_CHUNK_MISSING("noise.chunk_missing"),
  CHUNK_CREATED("chunk.created"),
  CHUNK_ERROR("chunk.error"),
  CHUNK_TRAVERSE("chunk.traverse"),
  CHUNK_TRAVERSE_CACHE_MISS("chunk.traverse.cache_miss"),
  TRAVERSAL_COMPLETED("traversal.completed"),
  TRAVERSAL_STEP("traversal.step"),
  LOOT_APPLIED("loot.applied"),
}

object TerrasectInstr {
  val structure: ScopedInstr = Instr.scoped(TerrasectInstrScope.STRUCTURE)
  val climate: ScopedInstr = Instr.scoped(TerrasectInstrScope.CLIMATE)
  val noise: ScopedInstr = Instr.scoped(TerrasectInstrScope.NOISE)
  val chunk: ScopedInstr = Instr.scoped(TerrasectInstrScope.CHUNK)
  val traversal: ScopedInstr = Instr.scoped(TerrasectInstrScope.TRAVERSAL)
  val loot: ScopedInstr = Instr.scoped(TerrasectInstrScope.LOOT)
}
