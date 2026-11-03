"""Replace only Carateen in the existing builds catalog after tests pass."""

import hashlib
import json
from pathlib import Path
import shutil
import sys
from urllib.parse import urlparse
import zipfile


def publish(source: Path, destination: Path) -> None:
    entry = json.loads((source / "Carateen/build/plugin-entry.json").read_text(encoding="utf-8"))
    package = source / "Carateen/build/Carateen.cs3"
    repo = json.loads((source / "repo.json").read_text(encoding="utf-8"))
    catalog_path = destination / "plugins.json"
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))

    if entry["internalName"] != "Carateen":
        raise ValueError("Unexpected plugin entry")
    digest = "sha256-" + hashlib.sha256(package.read_bytes()).hexdigest()
    if entry["fileHash"] != digest or entry["fileSize"] != package.stat().st_size:
        raise ValueError("Package does not match its catalog entry")
    with zipfile.ZipFile(package) as archive:
        if "classes.dex" not in archive.namelist():
            raise ValueError("Missing Android plugin code")
        manifest = json.loads(archive.read("manifest.json"))
        if manifest["version"] != entry["version"]:
            raise ValueError("Package and catalog versions differ")

    # Keep all other providers and their original attribution/download locations.
    entry["url"] = repo["pluginLists"][0].rsplit("/", 1)[0] + "/Carateen.cs3"
    repo_parts = urlparse(entry["url"]).path.strip("/").split("/")[:2]
    entry["repositoryUrl"] = "https://github.com/" + "/".join(repo_parts)
    catalog = [item for item in catalog if item.get("internalName") != "Carateen"]
    catalog.append(entry)
    shutil.copyfile(package, destination / "Carateen.cs3")
    catalog_path.write_text(json.dumps(catalog, ensure_ascii=False, indent=4) + "\n", encoding="utf-8")
    (destination / "repo.json").write_text(json.dumps(repo, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Published Carateen v{entry['version']} ({entry['fileSize']} bytes, {digest})")
    print(f"Repository retains {len(catalog)} providers")


if __name__ == "__main__":
    publish(Path(sys.argv[1]), Path(sys.argv[2]))
