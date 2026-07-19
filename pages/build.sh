#!/usr/bin/env bash
# Renders content/*.md with pandoc and stitches the result into one static page
# (pages/dist/index.html) with an anchor per section. Requires pandoc:
# https://pandoc.org/installing.html (`brew install pandoc` on macOS).
set -euo pipefail
cd "$(dirname "$0")"

OUT="dist"
rm -rf "$OUT"
mkdir -p "$OUT"
cp -R images "$OUT/images"
cp style.css "$OUT/style.css"

render() {
  # --no-highlight (not the newer --syntax-highlighting=none — unsupported by the older pandoc
  # preinstalled on GitHub's runners): syntax highlighting gives every code block id="cb1",
  # "cb2", ... starting from 1 in each pandoc invocation, which collide once every section is
  # concatenated onto one page. Plain <pre><code> also matches this site's low-key styling.
  pandoc --from gfm --to html5 --wrap=none --no-highlight "content/$1.md"
}

SUMMARY="$(cat content/summary.txt)"

# slug|Section <h1>|Nav label (the two differ only for Commands & Debug UI)
SECTIONS=(
  "getting-started|Getting Started|Getting Started"
  "regions|Regions|Regions"
  "decorations|Decorations|Decorations"
  "noise|Noise &amp; Climate|Noise &amp; Climate"
  "archetypes|Archetypes|Archetypes"
  "structures|Structures|Structures"
  "mobs|Mobs|Mobs"
  "loot|Loot|Loot"
  "commands|Commands &amp; Debug UI|Commands"
)

{
  cat <<HTML
<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Terrasect — region-based Minecraft world generation</title>
    <meta name="description" content="$SUMMARY" />
    <link rel="icon" href="data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2290%22>🧩</text></svg>" />
    <link rel="stylesheet" href="style.css" />
  </head>
  <body>
    <nav class="sitenav wrap" aria-label="Documentation">
      <a href="#overview">Home</a>
HTML
  for entry in "${SECTIONS[@]}"; do
    IFS='|' read -r slug _ nav_label <<<"$entry"
    echo "      <a href=\"#${slug}\">${nav_label}</a>"
  done
  cat <<HTML
    </nav>

    <header class="hero">
      <div class="wrap">
        <p class="eyebrow">Minecraft mod · Fabric &amp; NeoForge</p>
        <h1>Terrasect</h1>
        <p class="summary">$SUMMARY</p>
        <nav class="links" aria-label="Project links">
          <a href="https://github.com/let-value/terrasect">GitHub</a>
          <a href="https://github.com/let-value/terrasect/releases">Releases</a>
          <a href="https://github.com/let-value/terrasect/issues">Issues</a>
        </nav>
      </div>
    </header>

    <main class="wrap">
      <article>
        <section id="overview">
$(render description)
        </section>
HTML
  for entry in "${SECTIONS[@]}"; do
    IFS='|' read -r slug heading _ <<<"$entry"
    echo "        <section id=\"${slug}\">"
    echo "          <h1>${heading}</h1>"
    render "$slug"
    echo "        </section>"
  done
  cat <<HTML
      </article>
    </main>

    <footer class="wrap">
      <p>Terrasect is MIT licensed. Built with a lot of help from LLMs — see the license note above.</p>
    </footer>
  </body>
</html>
HTML
} > "$OUT/index.html"

echo "Built $OUT/index.html"
