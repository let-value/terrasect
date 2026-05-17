# Goal: Structure Constraints Locate Investigation

This investigation showed that `/locate` follows a separate pipeline through `ChunkGenerator.findNearestMapStructure(...)` and therefore bypasses the generation-only hook, so the shared `StructureHandler.getFilteredSets(...)` logic would need a second locate-path mixin to filter candidate structures per chunk position; that gap is now closed in the branch and the investigation is complete.
