import contextlib
import base64
import io
import json
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from unittest.mock import patch

import migrate_local_inventory as migration


class InventoryMigrationTest(unittest.TestCase):
    def setUp(self):
        self.app_dir = "/data/user/0/com.example.first"
        self.fox = dict(id=5, name="cool", previewPath=f"{self.app_dir}/app_fox_previews/fox_5.png",
                        placements=[dict(itemId=102, centerX=0.55, centerY=0.08, scale=1, rotation=0)],
                        foxFace=401)
        root = ET.Element("map")
        ET.SubElement(root, "string", name="foxes").text = json.dumps([self.fox])
        self.files = {
            "shared_prefs/fox_prefs.xml": ET.tostring(root),
            "shared_prefs/shop_point_prefs.xml": b'<map><int name="current_point" value="5300" /></map>',
            "shared_prefs/alchemy_selection.xml": b"<map />",
            "shared_prefs/alchemy_selection__user_20.xml": b"<map />",
            "shared_prefs/alchemy_selection__user_21.xml": b"<map />",
            "app_fox_previews/fox_5.png": b"\x89PNG\r\n\x1a\n\x00original PNG bytes",
        }

    def plan(self):
        return migration.build_plan(20, self.app_dir, self.files.get)

    def test_empty_target_keeps_legacy_data_and_rehomes_preview(self):
        before = dict(self.files)
        original, scoped, images, target_before, count = self.plan()
        foxes = json.loads(ET.fromstring(scoped["shared_prefs/fox_prefs__user_20.xml"]).find("string").text)
        self.assertEqual(foxes[0], {**self.fox, "previewPath": f"{self.app_dir}/app_fox_previews__user_20/fox_5.png"})
        self.assertEqual(scoped["shared_prefs/shop_point_prefs__user_20.xml"], self.files["shared_prefs/shop_point_prefs.xml"])
        self.assertEqual(images["app_fox_previews__user_20/fox_5.png"], self.files["app_fox_previews/fox_5.png"])
        self.assertEqual(target_before, {"shared_prefs/alchemy_selection__user_20.xml": b"<map />"})
        self.assertEqual(original["app_fox_previews/fox_5.png"], self.files["app_fox_previews/fox_5.png"])
        self.assertEqual(count, 1)
        self.assertEqual(before, self.files)

    def test_populated_target_is_never_overwritten(self):
        self.files["shared_prefs/item_inventory__user_20.xml"] = b'<map><int name="101" value="1" /></map>'
        with self.assertRaisesRegex(RuntimeError, "refusing to overwrite"):
            self.plan()

    def test_pending_android_backup_is_not_overwritten(self):
        self.files["shared_prefs/item_inventory__user_20.xml.bak"] = b"<map />"
        with self.assertRaisesRegex(RuntimeError, "pending preferences backup"):
            self.plan()

    def test_missing_image_aborts_instead_of_dropping_preview(self):
        del self.files["app_fox_previews/fox_5.png"]
        with self.assertRaisesRegex(RuntimeError, "Missing legacy preview"):
            self.plan()

    def test_empty_source_is_not_reported_as_success(self):
        with self.assertRaisesRegex(RuntimeError, "No legacy inventory"):
            migration.build_plan(20, self.app_dir, lambda _: None)

    def test_apply_backs_up_preserves_other_account_and_blocks_second_assignment(self):
        before = dict(self.files)
        with tempfile.TemporaryDirectory() as tmp:
            backup = Path(tmp) / "backup"

            def run(command, input=None, **kwargs):
                args = command[3:]
                stdout, code = b"", 0
                if args[:3] == ["shell", "am", "force-stop"]:
                    pass
                else:
                    operation = args[args.index("run-as") + 2:]
                    verb, *params = operation
                    if verb == "test":
                        code = 0 if params[1] in self.files else 1
                    elif verb == "cat":
                        stdout = self.files[params[0]]
                    elif verb == "pwd":
                        stdout = self.app_dir.encode()
                    elif verb == "mkdir":
                        pass
                    elif verb == "sh":
                        # Every device write must have a durable, verified original backup first.
                        self.assertEqual((backup / "legacy/app_fox_previews/fox_5.png").read_bytes(), before["app_fox_previews/fox_5.png"])
                        destination = params[1].removeprefix("'base64 -d > ").removesuffix("'")
                        self.assertTrue(input.isascii())
                        self.files[destination] = base64.b64decode(input, validate=True)
                    elif verb == "mv":
                        self.files[params[1]] = self.files.pop(params[0])
                    else:
                        self.fail(f"Unexpected adb operation: {operation}")
                return subprocess.CompletedProcess(command, code, stdout, b"")

            argv = ["migrate", "--serial", "test", "--owner-id", "20", "--backup-dir", str(backup)]
            with patch.object(sys, "argv", argv), patch.object(migration.subprocess, "run", side_effect=run), contextlib.redirect_stdout(io.StringIO()):
                migration.main()
                self.assertEqual(self.files, before)
                self.assertFalse(backup.exists())
                sys.argv.append("--apply")
                migration.main()
                for path, content in before.items():
                    self.assertEqual(self.files[path], content)
                self.assertTrue(json.loads(self.files[migration.ASSIGNMENT])["applied"])
                self.assertTrue((backup / "target_before/shared_prefs/alchemy_selection__user_20.xml").exists())
                sys.argv[sys.argv.index("--owner-id") + 1] = "21"
                with self.assertRaisesRegex(RuntimeError, "duplicate import"):
                    migration.main()


if __name__ == "__main__":
    unittest.main()
