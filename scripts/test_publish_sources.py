import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from publish_sources import publish, validate_package


class PublicationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source, self.dest = self.root / "src", self.root / "dist"
        build = self.source / "Demo/build"
        build.mkdir(parents=True)
        self.dest.mkdir()
        self.package = build / "Demo.cs3"
        with zipfile.ZipFile(self.package, "w") as archive:
            archive.writestr("classes.dex", b"test dex payload")
            archive.writestr("manifest.json", '{"name":"Demo","version":1}')
        data = self.package.read_bytes()
        self.entry = {"internalName": "Demo", "name": "Demo", "version": 1, "fileHash": "sha256-" + hashlib.sha256(data).hexdigest(), "fileSize": len(data)}
        (build / "plugin-entry.json").write_text(json.dumps(self.entry))
        (self.source / "repo.json").write_text(json.dumps({"name": "Example", "pluginLists": ["https://raw.githubusercontent.com/owner/repo/refs/heads/builds/plugins.json"]}))
        (self.source / "catalog-policy.json").write_text(json.dumps({"managedModules": ["Demo"], "retired": {"Dead": "confirmed broken"}, "groups": {"cartoons": {"name": "Cartoons", "modules": ["Demo", "Existing"]}}}))
        (self.dest / "plugins.json").write_text(json.dumps([{"internalName": "Existing", "name": "Existing", "url": "https://upstream.example/Existing.cs3", "version": 9, "fileHash": "keep"}, {"internalName": "Dead", "name": "Dead"}]))

    def test_package_integrity_rejects_corruption(self):
        self.package.write_bytes(self.package.read_bytes() + b"corrupted")
        with self.assertRaises(ValueError):
            validate_package(self.source, "Demo")

    def test_grouped_publication_retires_only_selected_provider_and_is_idempotent(self):
        publish(self.source, self.dest)
        first = json.loads((self.dest / "plugins.json").read_text())
        self.assertEqual({"Demo", "Existing"}, {item["internalName"] for item in first})
        existing = next(item for item in first if item["internalName"] == "Existing")
        self.assertEqual("keep", existing["fileHash"])
        self.assertEqual("https://upstream.example/Existing.cs3", existing["url"])
        demo = next(item for item in first if item["internalName"] == "Demo")
        self.assertEqual("https://github.com/owner/repo", demo["repositoryUrl"])
        self.assertEqual("[Cartoons] Demo", demo["name"])
        publish(self.source, self.dest)
        self.assertEqual(first, json.loads((self.dest / "plugins.json").read_text()))
        self.assertEqual(first, json.loads((self.dest / "cartoons.json").read_text()))


if __name__ == "__main__":
    unittest.main()
