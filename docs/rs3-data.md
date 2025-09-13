RS3 Data Fetcher

Overview
- Script: `scripts/fetch_rs3_data.sh`
- Output dir: `datasets/rs3/` (gitignored)

What it pulls (best-effort)
- RS Wiki (Cargo): abilities, lodestones, banks, NPCs, objects (full + tree-chop subset), teleport data, skill XP table.
- Official RS3 Item DB: detail + price graphs for a handful of common item IDs.
- Hiscores lite: a few well-known RS3 accounts.

Notes
- Cargo table names/fields can change; the script will still write responses so you can inspect errors and adjust queries.
- Be respectful of rate limits; the script uses curl retries and a limited item list by default.

Usage
- Run: `bash scripts/fetch_rs3_data.sh`
- Results land under `datasets/rs3/` with a timestamped log in `datasets/rs3/fetch.log`.
- If Cargo calls fail due to Cloudflare, set a Cookie header copied from your browser session:
  - Export `RS3_WIKI_COOKIE` with the cookie string (quote it), e.g.
    - `export RS3_WIKI_COOKIE='GeoIP=...; session=...; cf_clearance=...'`
  - Optionally set `RS3_WIKI_HEADERS` to a file containing additional `-H` headers for curl (one per line).
  - Re-run the fetch script; wiki requests will include these headers.

Extending
- Add more Cargo queries by appending more `cargo_query` calls.
- For WHERE clauses that include quotes/spaces, use `cargo_query_where <name> "<where-clause>" <kv-pairs...>`.
- Extend `ITEM_IDS` for more RS3 items if you want more coverage.
