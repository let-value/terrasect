# Goal: Structure Constraints Locate Implementation

This implementation goal closed the `/locate` gap by adding the locate-path mixin that filters candidate `Holder<Structure>` entries using the same chunk-aware `StructureHandler.getFilteredSets(...)` decision as world generation, keeping the existing `structures-constraints` branch and PR #51, and verifying the result with `./gradlew :common:compileJava :common:compileKotlin`.
