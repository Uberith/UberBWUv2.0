RS3 Data Fetcher

Overview
- Script: `scripts/fetch_rs3_data.sh`
- Output dir: `datasets/rs3/` (gitignored)

What it pulls (best-effort)
- RS Wiki (Cargo): abilities, lodestones, banks, NPCs, objects, teleport data, skill XP table.
- Official RS3 Item DB: detail + price graphs for a handful of common item IDs.
- Hiscores lite: a few well-known RS3 accounts.

Notes
- Cargo table names/fields can change; the script will still write responses so you can inspect errors and adjust queries.
- Be respectful of rate limits; the script uses curl retries and a limited item list by default.

Usage
- Run: `bash scripts/fetch_rs3_data.sh`
- Results land under `datasets/rs3/` with a timestamped log in `datasets/rs3/fetch.log`.

Extending
- Add more Cargo queries by appending more `cargo_query` calls.
- Extend `ITEM_IDS` for more RS3 items if you want more coverage.
