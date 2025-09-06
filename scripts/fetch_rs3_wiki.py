#!/usr/bin/env python3
import json
import os
import sys
import time
import urllib.parse
import urllib.request
from typing import Dict, Any, List

BASE = "https://runescape.wiki/w/api.php"
UA = "UberBWUv2.0-fetch/1.0 (RS3 wiki archival; contact: local dev)"
OUT_DIR = os.path.join("datasets", "rs3", "wiki")


def http_get(params: Dict[str, Any], attempt: int = 1) -> Dict[str, Any]:
    qs = urllib.parse.urlencode(params)
    req = urllib.request.Request(f"{BASE}?{qs}", headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            data = resp.read()
            return json.loads(data)
    except Exception as e:
        if attempt < 3:
            time.sleep(1.5 * attempt)
            return http_get(params, attempt + 1)
        raise e


def list_category_members(category: str, limit: int = 10000) -> List[Dict[str, Any]]:
    all_pages: List[Dict[str, Any]] = []
    cont: Dict[str, Any] = {}
    while True:
        params = {
            "action": "query",
            "format": "json",
            "formatversion": 2,
            "list": "categorymembers",
            "cmtitle": category,
            "cmtype": "page",
            "cmlimit": "max",
        }
        params.update(cont)
        data = http_get(params)
        pages = data.get("query", {}).get("categorymembers", [])
        all_pages.extend(pages)
        if len(all_pages) >= limit:
            break
        if "continue" in data:
            cont = data["continue"]
        else:
            break
    return all_pages


def search_pages(query: str, limit: int = 500) -> List[str]:
    titles: List[str] = []
    cont: Dict[str, Any] = {}
    while True:
        params = {
            "action": "query",
            "format": "json",
            "formatversion": 2,
            "list": "search",
            "srsearch": query,
            "srlimit": "50",
            "srnamespace": "0",  # main namespace
        }
        params.update(cont)
        data = http_get(params)
        hits = data.get("query", {}).get("search", [])
        for h in hits:
            t = h.get("title")
            if t:
                titles.append(t)
        if len(titles) >= limit:
            break
        if "continue" in data:
            cont = data["continue"]
        else:
            break
    return titles

def fetch_page_wikitext(title: str) -> str:
    data = http_get(
        {
            "action": "query",
            "format": "json",
            "formatversion": 2,
            "prop": "revisions",
            "rvprop": "content",
            "rvslots": "main",
            "titles": title,
        }
    )
    pages = data.get("query", {}).get("pages", [])
    if not pages:
        return ""
    revs = pages[0].get("revisions", [])
    if not revs:
        return ""
    slot = revs[0].get("slots", {}).get("main", {})
    return slot.get("content", "")


def fetch_page_html(title: str) -> str:
    data = http_get(
        {
            "action": "parse",
            "format": "json",
            "page": title,
            "prop": "text",
            "formatversion": 2,
        }
    )
    return data.get("parse", {}).get("text", "")


def safe_name(title: str) -> str:
    s = title.replace("/", "_").replace(":", "_")
    return "".join(ch for ch in s if ch.isalnum() or ch in ("_", "-", " ")).strip().replace(" ", "_")


def ensure_dir(path: str) -> None:
    os.makedirs(path, exist_ok=True)


def write_text(path: str, text: str) -> None:
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def write_json(path: str, obj: Any) -> None:
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def main() -> int:
    # Target categories — configurable via RS3_CATEGORIES env (comma-separated)
    default_categories = [
        "Category:Banks",
        "Category:Lodestones",
        "Category:Teleports",
        "Category:Abilities",
        "Category:Monsters",
        "Category:Fishing spots",
        "Category:Trees",
        "Category:Mining nodes",
        "Category:Shops",
        "Category:Quests",
    ]
    env_categories = os.environ.get("RS3_CATEGORIES", "").strip()
    categories = (
        [c.strip() for c in env_categories.split(",") if c.strip()]
        if env_categories
        else default_categories
    )

    # Max total pages (to bound initial runs) — override with RS3_MAX_PAGES
    try:
        max_pages = int(os.environ.get("RS3_MAX_PAGES", "2000"))
    except ValueError:
        max_pages = 2000

    # Prepare folders
    ensure_dir(OUT_DIR)
    pages_dir = os.path.join(OUT_DIR, "pages")
    ensure_dir(pages_dir)

    index = {"categories": {}, "fetched": []}

    # Gather pages per category
    for cat in categories:
        try:
            pages = list_category_members(cat)
        except Exception as e:
            pages = []
        index["categories"][cat] = {"count": len(pages), "pages": pages}

    # De-duplicate titles across categories
    seen = set()
    titles: List[str] = []
    for cat, meta in index["categories"].items():
        for p in meta.get("pages", []):
            t = p.get("title")
            if t and t not in seen:
                seen.add(t)
                titles.append(t)

    # If categories didn’t yield results, fall back to search
    fallback_map = {
        "Category:Banks": "bank",
        "Category:Lodestones": "lodestone",
        "Category:Teleports": "teleport",
        "Category:Abilities": "ability",
        "Category:Monsters": "monster",
        "Category:Fishing spots": "fishing spot",
        "Category:Trees": "tree",
        "Category:Mining nodes": "mining node",
        "Category:Shops": "shop",
        "Category:Quests": "quest",
    }
    for cat in categories:
        if index["categories"].get(cat, {}).get("count", 0) == 0:
            q = fallback_map.get(cat) or cat.replace("Category:", "")
            try:
                found = search_pages(q, limit=200)
            except Exception:
                found = []
            # Update index for transparency
            index["categories"][cat]["search_query"] = q
            index["categories"][cat]["search_hits"] = len(found)
            for t in found:
                if t not in seen:
                    seen.add(t)
                    titles.append(t)

    # Apply total cap if needed
    if len(titles) > max_pages:
        titles = titles[:max_pages]

    # Fetch content for each title (wikitext + HTML)
    for i, title in enumerate(titles):
        name = safe_name(title)
        meta_path = os.path.join(pages_dir, f"{name}.meta.json")
        wt_path = os.path.join(pages_dir, f"{name}.wikitext")
        html_path = os.path.join(pages_dir, f"{name}.html")
        try:
            wt = fetch_page_wikitext(title)
            html = fetch_page_html(title)
            write_text(wt_path, wt)
            write_text(html_path, html)
            write_json(meta_path, {"title": title})
            index["fetched"].append({"title": title, "files": [wt_path, html_path]})
        except Exception as e:
            # Skip on failure, continue
            index["fetched"].append({"title": title, "error": str(e)})
        # Respectful pacing
        if (i + 1) % 10 == 0:
            time.sleep(0.5)

    write_json(os.path.join(OUT_DIR, "index.json"), index)
    return 0


if __name__ == "__main__":
    sys.exit(main())
