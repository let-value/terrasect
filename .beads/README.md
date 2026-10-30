# Terrasect task tracking

Beads is the shared task record for all project agents. Start with `bd prime`,
`bd status`, and `bd ready`. See [the workflow](../docs/AGENT_WORKFLOW.md) for
claiming work, handoffs, agent setup, worktrees, and backups.

GitHub issues are imported through `bd github` using a token retrieved from gh
for each operation. The live Dolt database is local and ignored by Git.
Configuration and agent hooks are tracked; source commits do not contain the
tasks. Dolt history is shared on the GitHub origin under `refs/dolt/data`.
Use `bd dolt pull` and `bd dolt push` to synchronize, and `bd bootstrap` after
cloning. Normal source pushes do not synchronize the database.
