"""Validate new Android packages, merge them into the fork, and publish grouped lists."""

import hashlib
import json
from pathlib import Path
import shutil
import sys
from urllib.parse import urlparse
import zipfile


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def validate_package(source, module):
    entry = read(source / module / "build/plugin-entry.json")
    package = source / module / "build" / f"{module}.cs3"
    if entry["internalName"] != module:
        raise ValueError(f"Unexpected plugin identity for {module}")
    digest = "sha256-" + hashlib.sha256(package.read_bytes()).hexdigest()
    if entry["fileHash"] != digest or entry["fileSize"] != package.stat().st_size:
        raise ValueError(f"Package integrity mismatch: {module}")
    with zipfile.ZipFile(package) as archive:
        manifest = json.loads(archive.read("manifest.json"))
        if "classes.dex" not in archive.namelist() or manifest["version"] != entry["version"]:
            raise ValueError(f"Invalid Android package: {module}")
    return entry, package


def publish(source, destination):
    policy, repo = read(source / "catalog-policy.json"), read(source / "repo.json")
    catalog = read(destination / "plugins.json")
    entries = {item["internalName"]: item for item in catalog}
    base = repo["pluginLists"][0].rsplit("/", 1)[0]
    repository_url = "https://github.com/" + "/".join(urlparse(base).path.strip("/").split("/")[:2])
    # Validate every package before changing any published files.
    packages = {module: validate_package(source, module) for module in policy["managedModules"]}
    for module, (entry, package) in packages.items():
        entry["url"] = f"{base}/{module}.cs3"
        entry["repositoryUrl"] = repository_url
        entries[module] = entry
    for module in policy["retired"]:
        entries.pop(module, None)
    known = {module for group in policy["groups"].values() for module in group["modules"]}
    unknown = set(entries) - known
    if unknown:
        raise ValueError(f"Providers need a category before publication: {sorted(unknown)}")
    ordered = []
    for key, group in policy["groups"].items():
        group_entries = [entries[module] for module in group["modules"] if module in entries]
        prefix = f"[{key.title() if key != 'tools' else 'Other'}] "
        for entry in group_entries:
            if not entry["name"].startswith(prefix):
                entry["name"] = prefix + entry["name"]
        ordered.extend(group_entries)
        write(destination / f"{key}.json", group_entries)
        write(destination / f"repo-{key}.json", {
            **repo, "name": "MTG9T4 · " + group["name"], "pluginLists": [f"{base}/{key}.json"]
        })
    for module, (_, package) in packages.items():
        shutil.copyfile(package, destination / f"{module}.cs3")
    write(destination / "plugins.json", ordered)
    write(destination / "repo.json", repo)
    print(f"Published {len(packages)} validated packages; {len(ordered)} active providers in {len(policy['groups'])} categories")
    print("Retired:", ", ".join(policy["retired"]))


if __name__ == "__main__":
    publish(Path(sys.argv[1]), Path(sys.argv[2]))
