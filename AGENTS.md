# Terrasect Agent Guide

## Project Overview
- Build a Minecraft mod for narrative world partitioning that enables predictable journeys across the infinite world.
- Users manipulate temperature, biomes, structures, and mobs via a region registry describing regions and their relationships.

## Non-Functional Requirements
- **High performance hot paths:** Keep world generation code minimal and streamlined.
- **No allocations in hot paths:** Avoid creating garbage that could cause hitches during generation or rendering.
- **Be skeptical of caches:** Most logic runs once per block or chunk; avoid maintaining caches that will expire immediately.
- **Efficient math and lookups:** Prefer pre-allocated or table-driven lookups that avoid per-frame overhead.

## Project Structure
- `common`: Shared code usable across mod loaders.
- `fabric`: Fabric loader code (mixins, client game tests, other Fabric specifics).
- `neoforge`: NeoForge loader code (mixins and NeoForge specifics).
- `versions`: Dynamically created versioned projects inheriting from root projects to override behavior for older Minecraft versions.

## Tooling and Tasks
- Gradle task `unpackMinecraft`: Unpacks the latest or versioned Minecraft source for analysis and implementation.

## Agent Expectations
- Favor simple, allocation-free implementations in performance-sensitive paths.
- Keep documentation concise but comprehensive enough for quick onboarding.
- Align any new work with the region registry concept and predictable journey goal.
