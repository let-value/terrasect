# Terrasect Agent Guide

## Mission
Produce **readable, maintainable, explicit** code that advances Terrasect’s goal: **narrative world partitioning** that enables **predictable journeys across an infinite Minecraft world** via a **region registry** describing regions and their relationships.

## Style stance
Adopt a **direct, explicit, low-magic** approach:
- Prefer code that is easy to step through and reason about.
- Avoid “frameworks inside the mod” and unnecessary abstraction layers.
- Keep behavior discoverable from the code, not hidden behind indirection.

## Project overview
- Users manipulate **temperature, biomes, structures, and mobs** via a **region registry**.
- The region registry describes **regions** and **their relationships** (adjacency, progression, constraints).

## Non-functional requirements (hard constraints)
- **High-performance hot paths:** Worldgen/render-adjacent code stays minimal and streamlined.
- **No allocations in hot paths:** Avoid per-block/per-chunk garbage (collections, lambdas, boxing, streams, temporary objects).
- **Be skeptical of caches:** Many computations are single-use at block/chunk granularity; don’t build caches that churn.
- **Efficient math and lookups:** Prefer precomputed tables, compact representations, and predictable lookups.

## Minecraft-specific engineering rules
- **Lifecycle + side separation:** Keep client-only code isolated from common/server code.
- **Tick discipline:** Avoid heavy work in tick handlers; throttle, batch, and prefer event-driven updates.
- **Networking + sync:** Treat packets as untrusted input; validate/clamp; keep payloads small and versioned.
- **Serialization:** Centralize NBT/codec logic; define defaults explicitly; plan for migrations/backward compatibility.
- **Registries + init order:** Centralize registrations; keep init order explicit and safe.
- **Interop:** Prefer minimal, well-scoped mixins; guard optional integrations.

## Project structure (must follow)
- `common`: Shared code usable across mod loaders.
- `fabric`: Fabric loader code (mixins, client game tests, Fabric specifics).
- `neoforge`: NeoForge loader code (mixins, NeoForge specifics).
- `versions`: Dynamically created versioned projects inheriting from root projects to override behavior for older MC versions.

## Tooling
- Gradle task `unpackMinecraft`: Unpacks latest or versioned Minecraft source for analysis and implementation.

## Change scope rules
- Don’t make tiny edits in isolation. **Scan surrounding code** for consistency: naming, invariants, lifecycle, side rules, performance.
- If a **larger refactor** would materially improve clarity/safety/performance/compatibility:
  - **Propose it first** (rationale + concrete plan + risks).
  - **Do not start the big change without approval** unless explicitly asked.
- Keep each change **cohesive**: one intent per PR/commit.

## Implementation guidelines
- **Keep data + control flow obvious:** explicit state, explicit branching, explicit lifetimes.
- **Concrete first:** generalize only after proven duplication.
- **Invariants first:** state assumptions and enforce at boundaries (assertions in dev, validation in prod paths).
- **Functions:** small, single-purpose, low nesting; early returns are fine.
- **Naming:** descriptive and consistent; avoid vague “manager/service/controller” blobs.
- **State:** prefer explicit state objects; avoid hidden globals/singletons; isolate mutable state.
- **Errors/logging:** fail loudly in dev; in runtime paths log with context (dim/pos/id) and rate-limit spam.
- **Comments/docs:** explain *why*, invariants, and lifecycle constraints—not what the code already shows.
- **Formatting:** match project formatter/conventions; keep style uniform.

## Hot path checklist (worldgen/render-adjacent)
Before merging code that can run per block/chunk/tick:
- No new allocations (including implicit ones from streams/lambdas/autoboxing).
- No unbounded scans; all loops are bounded and justified.
- Lookups are table-driven or precomputed where sensible.
- Caches are justified with lifecycle + invalidation cost (default: no cache).

## Quality bar checklist (before finalizing)
- Does this align with **region registry** and **predictable journeys**?
- Can someone follow the data flow and invariants in one read?
- Are loader boundaries respected (`common` vs `fabric` vs `neoforge`)?
- Are init/registration points centralized and order-safe?
- Are hot paths allocation-free and minimal?
- Are serialization/network edges validated and future-proofed?
