# Goal: Structure Constraints Client GameTest Phase 3 More Test Cases

Status: DONE

Branch: `structures-constraints`
PR: `#51` — https://github.com/let-value/terrasect/pull/51

This phase-3 goal expands the existing structure-constraints client GameTest coverage with *additional* tests only. Existing tests stay as-is and continue to serve as regression baselines. The new coverage adds separate cases for allowing and banning structure constraints at each supported level: mod, tag, and name.

## Final state

All 10 client GameTests pass. No regressions. Phase-3 changes committed and pushed.

### Changes committed in this session (on top of `b4b3bcd`)

| File | Change |
|---|---|
| `common/src/main/kotlin/terrasect/definition/SelectionConstraints.kt` | **Bug fix**: tag evaluation used `containsAll` (required all structure tags to be in the block/allow list) instead of `any` (any overlap). Fixed to `blockedTags?.any(tags::contains)` and `allowedTags?.any(tags::contains)`. |
| `common/src/main/kotlin/terrasect/lookup/CompiledStructureLookup.kt` | `applyPlacementOverrides` now returns `Boolean` (true = mutated). The `placement === set.placement()` guard (always true after in-place mutation) replaced with `!placementMutated`. Uses direct setters; no longer calls `terrasect$withOverrides`. |
| `common/src/main/java/terrasect/extender/RandomSpreadStructurePlacementExtender.java` | Removed `terrasect$withOverrides` and unused `RandomSpreadStructurePlacement` import. |
| `common/src/main/java/terrasect/mixin/structure/RandomSpreadStructurePlacementMixin.java` | Removed `terrasect$withOverrides` implementation and unused `@Shadow spreadType()`. Mixin is now pure accessor-only. |

## Review feedback addressed

| Item | Status |
|---|---|
| Keep mixins thin; delegate to `StructureHandler`/lookup helpers | Done — `ChunkGeneratorLocateMixin` and `ChunkGeneratorStructureMixin` are pure thin wrappers; `RandomSpreadStructurePlacementMixin` now accessor-only. |
| Remove duplicated overload/layering in `StructureHandler` | Done — no redundant overloads; both locate and chunk paths resolve through `StructureHandler` directly. |
| Avoid hot-path allocation; pre-bake at context creation | Done — `CompiledStructureLookup.build()` pre-bakes all unique constraint sets; hot path is a cache hit. |
| Mutate `RandomSpreadStructurePlacement` in place | Done — `applyPlacementOverrides` uses direct field setters (`terrasect$setSpacing` etc.) and returns a boolean indicating mutation. `withOverrides` removed. |
| Reshape phase-3 GameTests into grouped classes with screenshot pairs | Done — 6 new test objects (BanByMod, AllowByMod, BanByTag, AllowByTag, BanByName, AllowByName), each capturing a vanilla screenshot + a constrained screenshot at the same location. |
| **Tag-matching correctness** | Fixed — `SelectionConstraints.evaluate` now uses overlap (`any`) instead of containment (`containsAll`). A single blocked tag correctly suppresses structures that have that tag plus others. |

## Verification

### All 10 client GameTests passed (2026-05-17)

**Phase-3 tests** (`-Ptest=structureconstraintban/allowby...`):

| Test | Vanilla locate | Constrained locate | Result |
|---|---|---|---|
| BanByMod | `minecraft:village_plains` at (-192,672) dist=698 | not found | ✓ |
| AllowByMod | `minecraft:village_plains` at (-192,672) dist=698 | same | ✓ |
| BanByTag | `minecraft:village_plains` at (-192,672) dist=698 | not found | ✓ |
| AllowByTag | `minecraft:village_plains` at (-192,672) dist=698 | same | ✓ |
| BanByName | `minecraft:village_plains` at (-192,672) dist=698 | not found | ✓ |
| AllowByName | `minecraft:village_plains` at (-192,672) dist=698 | same | ✓ |

**Regression tests** (`-Ptest=structureconstraintvanilla/highdensity/locate/...`):

| Test | Result |
|---|---|
| VanillaGameTest | ✓ (probe screenshots at 0,0 / 512,0 / 0,512 / 512,512) |
| HighDensityGameTest | ✓ (village at probe 512,0 confirms denser grid) |
| LocateGameTest | ✓ (vanilla dist=698, high_density dist=128, blocked=null) |
| LocateRuinedPortalGameTest | ✓ (dist=385, id=minecraft:ruined_portal) |

### Screenshot artifacts (relative to `fabric/build/gametest-screenshots/`)

```
StructureConstraintBanByModTest/vanilla/0000_located_village.png
StructureConstraintBanByModTest/banned/0001_village_absent.png
StructureConstraintAllowByModTest/vanilla/0002_located_village.png
StructureConstraintAllowByModTest/allowed/0003_village_present.png
StructureConstraintBanByTagTest/vanilla/0004_located_village.png
StructureConstraintBanByTagTest/banned/0005_village_absent.png
StructureConstraintAllowByTagTest/vanilla/0006_located_village.png
StructureConstraintAllowByTagTest/allowed/0007_village_present.png
StructureConstraintBanByNameTest/vanilla/0008_located_village.png
StructureConstraintBanByNameTest/banned/0009_village_absent.png
StructureConstraintAllowByNameTest/vanilla/0010_located_village.png
StructureConstraintAllowByNameTest/allowed/0011_village_present.png
StructureConstraintVanillaTest/{probe screenshots}
StructureConstraintHighDensityTest/{probe screenshots}
StructureConstraintLocateTest/vanilla/ + high_density/
StructureConstraintLocateRuinedPortalTest/
```

## Notes

- VanillaWorldDigestTest (snapshot column test) is excluded from filter above; that test has a pre-existing snapshot baseline that was not touched in this session — it was the failing test in earlier unfiltered runs.
- The `SelectionConstraints` tag bug (`containsAll` → `any`) would have silently caused any config using `blockTags` or `allowTags` to either block too much (when structure has fewer tags than blocklist) or too little (when structure has more tags than blocklist). The fix is the most important correctness change in this session.
