#!/usr/bin/env bash
set -euo pipefail

# Simple RS3 data fetcher: RS Wiki (Cargo) + Official RS item APIs
# Outputs to datasets/rs3/*.json (gitignored)

OUT_DIR="datasets/rs3"
LOG_FILE="$OUT_DIR/fetch.log"
UA="UberBWUv2.0-fetch/1.0 (+https://runescape.wiki; contact: local dev)"

mkdir -p "$OUT_DIR/wiki" "$OUT_DIR/itemdb" "$OUT_DIR/hiscores"

log() {
  printf "[%s] %s\n" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$LOG_FILE" >&2
}

curl_json() {
  local url="$1"
  local out="$2"
  log "GET $url -> $out"
  curl -fsSL --retry 3 --retry-delay 2 -A "$UA" "$url" -o "$out" || {
    log "WARN: failed $url"
    return 1
  }
}

cargo_query() {
  local name="$1"; shift
  local out="$OUT_DIR/wiki/$name.json"
  local q
  q=$(printf "%s" "$*" | tr ' ' '&')
  local url="https://runescape.wiki/w/api.php?action=cargoquery&format=json&formatversion=2&${q}"
  curl_json "$url" "$out" || true
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

# Objects (limited fields)
cargo_query objects \
  tables=Objects \
  fields=id%2Cname%2Cactions \
  limit=500

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
