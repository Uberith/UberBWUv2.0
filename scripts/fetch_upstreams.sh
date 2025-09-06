#!/usr/bin/env bash
set -euo pipefail

# Clones/updates upstream repos into external/ for local scanning.
# Nothing is committed; external/ is gitignored.

OUT_DIR="external"
API_REPO="https://github.com/BotWithUs/BotWithUs-V2-API.git"
XAPI_REPO="https://github.com/BotWithUs/BotWithUs-V2-XAPI.git"

# Override branches via env, defaults to default branch of origin
API_REF="${API_REF:-}"
XAPI_REF="${XAPI_REF:-}"

mkdir -p "$OUT_DIR"

clone_or_update() {
  local url="$1"; shift
  local name="$1"; shift
  local ref="${1:-}"
  local dest="$OUT_DIR/$name"

  if [[ -d "$dest/.git" ]]; then
    echo "Updating $name..." >&2
    git -C "$dest" fetch --all --prune --tags --depth 1 || git -C "$dest" fetch --all --prune --tags
  else
    echo "Cloning $name..." >&2
    git clone --depth 1 "$url" "$dest"
  fi

  if [[ -n "$ref" ]]; then
    echo "Checking out $name @ $ref" >&2
    git -C "$dest" fetch origin "$ref" --depth 1 || true
    git -C "$dest" checkout -q "$ref" || git -C "$dest" checkout -q "origin/$ref" || true
  fi

  # Record current commit
  git -C "$dest" rev-parse HEAD > "$dest/.pinned-rev"
}

clone_or_update "$API_REPO" "BotWithUs-V2-API" "$API_REF"
clone_or_update "$XAPI_REPO" "BotWithUs-V2-XAPI" "$XAPI_REF"

echo "Done. Local copies in $OUT_DIR/." >&2

