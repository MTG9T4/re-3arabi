"""Audit configured provider URLs without calling a blocked site a dead provider."""

import concurrent.futures
from datetime import datetime, timezone
from html import unescape
import json
from pathlib import Path
import re
import subprocess
import tempfile
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
TEMP = Path(tempfile.gettempdir()) / "re-3arabi-audit"
TEMP.mkdir(exist_ok=True)
UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
REQUESTED = {
    "MySeed": "https://myseed.cam/",
    "CimaClub-requested": "https://w.cimacub.com/",
    "Iwaatch": "https://iwaatch.com/",
    "Iwaatch-sample": "https://iwaatch.com/movie/supergirl-2026",
    "SpacePowerFan": "https://spacepowerfan.com/",
    "ArabicCartoon": "https://arabiccartoon.org/home/",
    "Shooot": "https://shooot.me/",
    "OnlineSportLive": "https://onlinesportlive.com/",
}


def inventory():
    result = []
    for module in sorted(ROOT.iterdir()):
        if not (module / "build.gradle.kts").exists() or module.name.startswith("."):
            continue
        for file in sorted((module / "src/main").rglob("*.kt")):
            text = file.read_text(encoding="utf-8-sig")
            if not re.search(r"class\s+\w+[^{}]*:\s*MainAPI\s*\(", text):
                continue
            match = re.search(r'override\s+var\s+mainUrl\s*(?::\s*String)?\s*=\s*"([^"]+)"', text)
            if not match:
                match = re.search(r'val\s+defaultUrl\s*=\s*"([^"]+)"', text)
            if match:
                result.append({"provider": module.name, "url": match[1], "source": str(file.relative_to(ROOT))})
                break
    result += [{"provider": name, "url": url, "requested": True} for name, url in REQUESTED.items()]
    return result


def probe(item):
    item = dict(item)
    output = TEMP / (item["provider"] + ".html")
    attempts = []
    for attempt in range(2):
        command = ["curl.exe", "-sS", "-L", "--max-redirs", "6", "--connect-timeout", "8", "--max-time", "22", "-A", UA,
                   "-e", item["url"], "-o", str(output), "-w", "%{http_code}\n%{url_effective}\n%{content_type}", item["url"]]
        response = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace")
        fields = response.stdout.splitlines()
        status = int(fields[0]) if fields and fields[0].isdigit() else 0
        body = output.read_text(encoding="utf-8", errors="replace") if output.exists() else ""
        attempts.append({"http": status, "exit": response.returncode, "error": response.stderr.strip()[:400]})
        if status in (200, 401, 403, 405, 429) and response.returncode == 0:
            break
    title = re.search(r"<title[^>]*>(.*?)</title>", body, re.I | re.S)
    item.update({
        "http": status,
        "final_url": fields[1] if len(fields) > 1 else item["url"],
        "content_type": fields[2] if len(fields) > 2 else "",
        "title": unescape(re.sub(r"\s+", " ", title[1])).strip() if title else "",
        "body_chars": len(body),
        "protected": status in (401, 403, 429) or bool(title and re.search(r"Just a moment|captcha|access denied", title[1], re.I)),
        "parking_signals": re.findall(r"domain is for sale|buy this domain|sedoparking|hugedomains|domain has expired", body, re.I)[:5],
        "attempts": attempts,
    })
    print(json.dumps({k: item[k] for k in ("provider", "http", "final_url", "title", "protected", "parking_signals")}, ensure_ascii=False), flush=True)
    return item


if __name__ == "__main__":
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
        items = list(executor.map(probe, inventory()))
    report = {"checked_at": datetime.now(timezone.utc).isoformat(), "scope": "URL reachability only; HTTP 200 is not playback verification", "sources": items}
    folder = ROOT / "docs/audits"
    folder.mkdir(parents=True, exist_ok=True)
    destination = folder / "source-reachability-2026-11-03.json"
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Saved", destination)
