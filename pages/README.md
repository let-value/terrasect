# pages/

The GitHub Pages site for Terrasect, and the single source of truth for user-facing project copy —
this is what mod users see, not contributors. Nothing in here should describe implementation
details (mixins, packages, internal APIs); that belongs in `docs/` instead.

Deployed automatically to GitHub Pages when a release tag is pushed (see
`.github/workflows/pages.yml`).

## Layout

```
pages/
├── index.html         # Page shell: hero (title/summary/links) + a container for the description
├── style.css          # Modern CSS: native light/dark theming via light-dark(), no manual toggle
├── script.js          # Tiny dependency-free Markdown renderer; fetches content/ at page load
├── content/
│   ├── summary.txt    # One-line plain-text summary — mirrors Modrinth's "Summary" field
│   └── description.md # Long-form description — mirrors Modrinth's "Description" field
└── README.md          # This file
```

## Why the content is split out

`content/summary.txt` and `content/description.md` are structured the same way Modrinth splits a
project page (title, summary, description) so the same two files can be:

1. rendered client-side into `index.html` for the GitHub Pages site, and
2. pushed as-is to Modrinth's project description via their API (see below),

without maintaining two copies of the same marketing copy. Keep this content written for players —
no build tooling renders it, so what's in `content/` is exactly what ships both places.

## Publishing to Modrinth

`.github/workflows/pages.yml` has a `sync-modrinth` job that PATCHes `content/summary.txt` and
`content/description.md` straight to the Modrinth project (`PATCH /v2/project/{id}`) on every
release tag. It's `continue-on-error: true` and no-ops if the secrets aren't set, because the
`MODRINTH_TOKEN` already used by `publish.yml` is scoped for `version-create` and may not carry the
project-write permission this needs — if the job fails with an auth error, mint a token with
broader scope.

## CurseForge

CurseForge does not expose a public API for editing a project's page/description — only for
uploading version files (which `publish.yml` already does). Updating the CurseForge listing means
manually pasting the contents of `content/description.md` into their editor, reformatted for their
rich-text/BBCode input (it doesn't accept raw Markdown either). There's no way around that today.
