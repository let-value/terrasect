# Terrasect Project Map Generation

## Task
**Date:** 20260507
**Submitted By:** Hermes Agent (Orchestrator)
**Status:** PENDING

### Request
Build a comprehensive project map for the Terrasect codebase. The documentation needs to consolidate knowledge from the primary source directories: `common/`, `fabric/`, and `neoforge/`.

The resulting document must be saved to `docs/PROJECT_MAP.md` and cover the following required sections:
1.  Directory and file structure with a one-line purpose for each file.
2.  Key classes and what they own/do.
3.  The main data flow: how a region is defined, looked up, and applied during worldgen.
4.  Any assumptions baked into the code — magic numbers, undocumented invariants, implicit contracts between classes.

### Context
The goal of this documentation is to serve as a definitive, single source of truth for the entire project architecture, aiding onboarding and identifying potential architectural drift. The current system is focused on "narrative world partitioning" that enables "predictable journeys across an infinite Minecraft world via a region registry." The key components to examine are modularity boundaries (common, fabric, neoforge) and the core world generation piping. The specific directories requiring deep analysis are: /home/alex/terrasect/common/, /home/alex/terrasect/fabric/, and /home/alex/terrasect/neoforge/.

### Acceptance criteria
The resulting `docs/PROJECT_MAP.md` must be a comprehensive technical document that:
*   Is accurate to the current codebase (as determined by sub-agent analysis).
*   Provides high-level architectural clarity for a new senior engineer reading it the first time.
*   Separates structural comments from functional data flow descriptions.

---
**Sub-agent instruction:** The goal of this file is purely documentation. Read the required source code directories (`common/`, `fabric/`, `neoforge/`) and synthesize the required architectural map into a single markdown document. Write your complete response into the `## Response` section below before finishing. Update the `Status` field to `COMPLETED` when done.
