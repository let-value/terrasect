package terrasect.cache

import terrasect.generation.ChunkContext
import terrasect.lookup.CompiledNoiseRegistry

class NoiseConstraintCache(
    private val chunkContext: ChunkContext,
    private val registry: CompiledNoiseRegistry,
) {}
