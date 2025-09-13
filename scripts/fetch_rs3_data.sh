#!/usr/bin/env bash
set -euo pipefail

# Simple RS3 data fetcher: RS Wiki (Cargo) + Official RS item APIs
# Outputs to datasets/rs3/*.json (gitignored)

OUT_DIR="datasets/rs3"
LOG_FILE="$OUT_DIR/fetch.log"
# Keep UA simple to avoid shells mis-parsing semicolons
UA="UberBWUv2.0-fetch/1.0 (+https://runescape.wiki contact=local-dev)"

# Optional: extra curl flags (e.g., proxy), cookie/header injection for Cloudflare/API
# - RS3_CURL_EXTRA: raw flags appended to curl (e.g., "--proxy http://127.0.0.1:8080")
# - RS3_WIKI_COOKIE: literal Cookie header value copied from your browser (quote it)
# - RS3_WIKI_HEADERS: path to a file with extra -H headers (one per line), used only for wiki requests
RS3_CURL_EXTRA="${RS3_CURL_EXTRA:-}"
RS3_WIKI_COOKIE="${RS3_WIKI_COOKIE:-}"
RS3_WIKI_HEADERS_FILE="${RS3_WIKI_HEADERS:-}"

# Configurable pacing and alternate wiki endpoints (comma-separated)
RS3_DELAY="${RS3_DELAY:-0.8}"
# Use only the canonical MediaWiki API path. The plain /api.php often lacks Cargo.
RS3_WIKI_ENDPOINTS="${RS3_WIKI_ENDPOINTS:-https://runescape.wiki/w/api.php}"
IFS=',' read -r -a CARGO_ENDPOINTS <<< "$RS3_WIKI_ENDPOINTS"

mkdir -p "$OUT_DIR/wiki" "$OUT_DIR/itemdb" "$OUT_DIR/hiscores"

log() {
  printf "[%s] %s\n" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$LOG_FILE" >&2
}

curl_json() {
  local url="$1"
  local out="$2"
  log "GET $url -> $out"
  # Build curl args dynamically so we can add optional headers
  local args=( -fsSL --compressed --retry 5 --retry-all-errors --retry-delay 2 --max-time 60 --connect-timeout 10 -H "User-Agent: $UA" )
  if [[ "$url" == *"runescape.wiki"* ]]; then
    if [[ -n "$RS3_WIKI_COOKIE" ]]; then
      # Ensure cookie has no newlines (curl treats them as separate args)
      local cookie_compact
      cookie_compact=$(printf "%s" "$RS3_WIKI_COOKIE" | tr -d '\r' | tr -d '\n')
      args+=( -H "Cookie: ${cookie_compact}" -H "Referer: https://runescape.wiki/" -H "Origin: https://runescape.wiki" -H "Accept: application/json" )
    fi
    if [[ -n "$RS3_WIKI_HEADERS_FILE" && -f "$RS3_WIKI_HEADERS_FILE" ]]; then
      while IFS= read -r hdr; do
        [[ -n "$hdr" ]] && args+=( -H "$hdr" )
      done < "$RS3_WIKI_HEADERS_FILE"
    fi
  fi
  if [[ -n "$RS3_CURL_EXTRA" ]]; then
    # shellcheck disable=SC2086
    args+=( $RS3_CURL_EXTRA )
  fi
  args+=( "$url" -o "$out" )
  if [[ "${RS3_DEBUG:-}" == "1" ]]; then
    # Pretty-print the exact curl invocation for debugging
    printf "[debug] curl" | tee -a "$LOG_FILE" >&2
    for a in "${args[@]}"; do
      printf " %q" "$a" | tee -a "$LOG_FILE" >&2
    done
    printf "\n" | tee -a "$LOG_FILE" >&2
  fi
  # shellcheck disable=SC2068
  curl ${args[@]} || {
    log "WARN: failed $url"
    return 1
  }
}

cargo_query() {
  local name="$1"; shift
  local out="$OUT_DIR/wiki/$name.json"
  local q
  q=$(printf "%s" "$*" | tr ' ' '&')
  local ok=0
  for base in "${CARGO_ENDPOINTS[@]}"; do
    local url="${base}?action=cargoquery&format=json&formatversion=2&${q}"
    if curl_json "$url" "$out"; then
      ok=1
      break
    fi
  done
  # Pacing between queries
  sleep "$RS3_DELAY"
  if [[ "$ok" -eq 0 ]]; then
    log "WARN: all endpoints failed for cargo query: $name"
  fi
}

# cargo_query_where: helper for WHERE clauses with quotes/spaces, URL-encodes the WHERE
cargo_query_where() {
  local name="$1"; shift
  local where="$1"; shift
  local out="$OUT_DIR/wiki/$name.json"
  local q
  q=$(printf "%s" "$*" | tr ' ' '&')
  # URL-encode the WHERE clause using python or fallback to perl if python missing
  local enc_where
  if command -v python >/dev/null 2>&1; then
    enc_where=$(python - <<'PY'
import sys, urllib.parse
print(urllib.parse.quote(sys.stdin.read(), safe=''))
PY
    <<<"$where")
  else
    enc_where=$(perl -MURI::Escape -e 'print uri_escape(join("",<>));' <<<"$where")
  fi
  local ok=0
  for base in "${CARGO_ENDPOINTS[@]}"; do
    local url="${base}?action=cargoquery&format=json&formatversion=2&${q}&where=${enc_where}"
    if curl_json "$url" "$out"; then
      ok=1; break
    fi
  done
  sleep "$RS3_DELAY"
  if [[ "$ok" -eq 0 ]]; then
    log "WARN: all endpoints failed for cargo query (where): $name"
  fi
}

log "Starting RS3 data fetch into $OUT_DIR"

# RS Wiki (Cargo) — best-effort queries. Tables/fields may evolve over time.
# You can inspect https://runescape.wiki/Special:CargoTables for current schemas.

# Abilities (cooldowns/adrenaline/etc.). Table name/fields can change; this is a best-effort snapshot.
cargo_query abilities \
  tables=Abilities \
  fields=name%2Ccategory%2Ccooldown%2Cadrenaline_cost%2Cadrenaline_gain%2Cthreshold%2Cultimate%2Cdescription \
  limit=500

# Lodestones
cargo_query lodestones \
  tables=Lodestones \
  fields=name%2Clocation%2Cmembers%2Crequirements \
  limit=500

# Banks
cargo_query banks \
  tables=Banks \
  fields=name%2Clocation%2Cnotes \
  limit=500

# NPCs (id + name only to keep response small)
cargo_query npcs \
  tables=NPCs \
  fields=id%2Cname%2Cmembers \
  limit=500

# Objects (expanded limit) and tree-chop subset
cargo_query objects \
  tables=Objects \
  fields=id%2Cname%2Cactions \
  limit=5000

# Likely tree objects (actions include Chop down / Cut down)
cargo_query_where objects_chop \
  "actions HOLDS 'Chop down' OR actions HOLDS 'Cut down'" \
  tables=Objects \
  fields=id%2Cname%2Cactions \
  limit=5000

# Teleports (if present as a cargo table)
cargo_query teleports \
  tables=Teleports \
  fields=name%2Cdestination%2Crequirements%2Ccost \
  limit=500

# Skills XP tables (generic)
cargo_query skill_xp \
  tables=Experience_table \
  fields=level%2Cxp \
  limit=200

# Official RS3 Item DB — sample endpoints.
# Note: Full crawl is large and rate-limited; we snapshot a few key items.
declare -a ITEM_IDS=(995 9244 2364 6571 1513 1515 1521 590 554 555 556 557 558 559)
for id in "${ITEM_IDS[@]}"; do
  curl_json "https://secure.runescape.com/m=itemdb_rs/api/catalogue/detail.json?item=${id}" "$OUT_DIR/itemdb/detail_${id}.json" || true
  curl_json "https://secure.runescape.com/m=itemdb_rs/api/graph/${id}.json" "$OUT_DIR/itemdb/graph_${id}.json" || true
done

# Hiscores lite — snapshot a couple of well-known RS3 accounts
declare -a NAMES=("S U O M I" "Drumgun" "Lilyuffie88")
for name in "${NAMES[@]}"; do
  enc_name=${name// /%20}
  curl_json "https://secure.runescape.com/m=hiscore/index_lite.ws?player=${enc_name}" "$OUT_DIR/hiscores/${name// /_}.txt" || true
done

log "Fetch complete. Files in $OUT_DIR."
