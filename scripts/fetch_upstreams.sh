#!/usr/bin/env bash
set -euo pipefail

# Clones/updates upstream repos into external/ for local scanning.
# Nothing is committed; external/ is gitignored.

OUT_DIR="external"
API_REPO="https://github.com/BotWithUs/BotWithUs-V2-API.git"
XAPI_REPO="https://github.com/BotWithUs/BotWithUs-V2-XAPI.git"
# Also fetch the Uberith public repo with additional content
UBERBW_REPO="https://github.com/Uberith/UberBWUv2.git"

# Override branches via env, defaults to default branch of origin
API_REF="${API_REF:-}"
XAPI_REF="${XAPI_REF:-}"
# Optional: pin UberBWUv2 to a branch/tag/commit via UBERBW_REF
UBERBW_REF="${UBERBW_REF:-}"

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
clone_or_update "$UBERBW_REPO" "UberBWUv2" "$UBERBW_REF"

# Download selected Maven artifacts (e.g., ImGui) for offline/local use under external/maven
# Configure version via IMGUI_VERSION, defaults to a known recent.
IMGUI_VERSION="${IMGUI_VERSION:-1.0.2-20250818.161536-3}"
IMGUI_COORD_GROUP="net/botwithus/imgui"
IMGUI_ARTIFACT="imgui"
MAVEN_BASES=(
  "https://nexus.botwithus.net/repository/maven-releases"
  "https://nexus.botwithus.net/repository/maven-snapshots"
  "https://nexus.botwithus.net/repository/maven-public"
)

download_artifact() {
  local group_path="$1"; shift
  local artifact="$1"; shift
  local version="$1"; shift
  local dest_base="$OUT_DIR/maven/$group_path/$artifact/$version"
  mkdir -p "$dest_base"
  local jar="$artifact-$version.jar"
  local pom="$artifact-$version.pom"
  for base in "${MAVEN_BASES[@]}"; do
    local jar_url="$base/$group_path/$artifact/$version/$jar"
    local pom_url="$base/$group_path/$artifact/$version/$pom"
    echo "Attempting $jar_url" >&2
    if curl -fsSL "$jar_url" -o "$dest_base/$jar"; then
      echo "Saved $dest_base/$jar" >&2
      curl -fsSL "$pom_url" -o "$dest_base/$pom" || true
      return 0
    fi
  done
  echo "WARN: Failed to download $artifact:$version from configured repos" >&2
  return 1
}

download_artifact "$IMGUI_COORD_GROUP" "$IMGUI_ARTIFACT" "$IMGUI_VERSION" || true

# Also copy the jar to a flat directory for flatDir resolution
mkdir -p "$OUT_DIR/lib"
if [[ -f "$OUT_DIR/maven/$IMGUI_COORD_GROUP/$IMGUI_ARTIFACT/$IMGUI_VERSION/$IMGUI_ARTIFACT-$IMGUI_VERSION.jar" ]]; then
  cp -f "$OUT_DIR/maven/$IMGUI_COORD_GROUP/$IMGUI_ARTIFACT/$IMGUI_VERSION/$IMGUI_ARTIFACT-$IMGUI_VERSION.jar" \
        "$OUT_DIR/lib/$IMGUI_ARTIFACT-$IMGUI_VERSION.jar"
fi

echo "Done. Local copies in $OUT_DIR/." >&2
