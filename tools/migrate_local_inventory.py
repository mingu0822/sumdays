"""Assign legacy, device-wide inventory to an explicitly chosen local account.

Requires a debuggable Android app and adb. Dry-run by default. --apply stops the
app, backs up the inventory, then copies it into the owner's account namespace.
Original files and authentication data are never changed. Empty scoped preference
files created by opening the app are allowed; populated account data is never
overwritten. The assignment is recorded on the device to prevent duplicate imports.
"""

import argparse
import base64
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path, PurePosixPath


PREFERENCES = (
    "owned_items", "item_inventory", "shop_point_prefs", "theme_settings",
    "fox_settings", "fox_prefs", "alchemy_selection",
)
ASSIGNMENT = "files/legacy_inventory_assignment.json"


def build_plan(owner_id, app_directory, read_optional):
    """Validate the entire import before writing anything to the device."""
    suffix = f"__user_{owner_id}"
    target_directory = f"app_fox_previews{suffix}"
    original, scoped, images, target_before = {}, {}, {}, {}
    fox_count = 0

    for name in PREFERENCES:
        target = f"shared_prefs/{name}{suffix}.xml"
        if read_optional(f"{target}.bak") is not None:
            raise RuntimeError(f"Target has a pending preferences backup: {target}; manual review required.")
        content = read_optional(target)
        if content is not None:
            root = ET.fromstring(content)
            if root.tag != "map" or len(root) or (root.text or "").strip():
                raise RuntimeError(f"Target already has {name}; refusing to overwrite account data.")
            target_before[target] = content

    for name in PREFERENCES:
        source = f"shared_prefs/{name}.xml"
        content = read_optional(source)
        if content is None:
            continue
        original[source] = content
        root = ET.fromstring(content)
        if root.tag != "map":
            raise RuntimeError(f"Invalid preferences in {source}")
        if name == "fox_prefs":
            foxes_node = root.find("./string[@name='foxes']")
            if foxes_node is not None and foxes_node.text:
                foxes = json.loads(foxes_node.text)
                fox_count = len(foxes)
                for fox in foxes:
                    preview = fox.get("previewPath")
                    if not preview:
                        continue
                    old_path = PurePosixPath(preview)
                    if old_path.parent.name != "app_fox_previews" or not re.fullmatch(r"fox_\d+\.png", old_path.name):
                        raise RuntimeError("Unexpected legacy preview path; manual review required.")
                    source_image = f"app_fox_previews/{old_path.name}"
                    image = read_optional(source_image)
                    if image is None:
                        raise RuntimeError(f"Missing legacy preview: {source_image}; original data left intact.")
                    images[f"{target_directory}/{old_path.name}"] = image
                    original[source_image] = image
                    fox["previewPath"] = f"{app_directory}/{target_directory}/{old_path.name}"
                foxes_node.text = json.dumps(foxes, ensure_ascii=False, separators=(",", ":"))
                content = ET.tostring(root, encoding="utf-8", xml_declaration=True)
        scoped[f"shared_prefs/{name}{suffix}.xml"] = content
    if not scoped:
        raise RuntimeError("No legacy inventory was found; nothing to migrate.")
    return original, scoped, images, target_before, fox_count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default="com.example.first")
    parser.add_argument("--owner-id", required=True, type=int)
    parser.add_argument("--backup-dir", required=True, type=Path)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    if args.owner_id < 0 or not re.fullmatch(r"[a-zA-Z0-9_.]+", args.package):
        parser.error("A nonnegative owner ID and valid package name are required.")

    def adb(*command, data=None, check=True):
        result = subprocess.run(
            [args.adb, "-s", args.serial, *command], input=data,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30,
        )
        if check and result.returncode:
            raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
        return result

    def exists(path):
        return adb("shell", "run-as", args.package, "test", "-e", path, check=False).returncode == 0

    def read(path):
        return adb("exec-out", "run-as", args.package, "cat", path).stdout

    def write(path, content):
        temporary = f"{path}.migration_tmp"
        # Windows adb can treat piped stdin as text (CRLF and Ctrl-Z). Transport
        # ASCII only, then decode inside the app's UID before verifying bytes.
        adb("shell", "-T", "run-as", args.package, "sh", "-c",
            f"'base64 -d > {temporary}'", data=base64.b64encode(content))
        if read(temporary) != content:
            raise RuntimeError(f"Verification failed for {temporary}")
        adb("shell", "run-as", args.package, "mv", temporary, path)
        if read(path) != content:
            raise RuntimeError(f"Verification failed for {path}")

    suffix = f"__user_{args.owner_id}"
    if exists(ASSIGNMENT):
        raise RuntimeError("Legacy inventory already has an assignment record; refusing a duplicate import.")
    if args.apply:
        if args.backup_dir.exists():
            raise RuntimeError("Use a new backup directory to preserve prior backups.")
        adb("shell", "am", "force-stop", args.package)

    target_directory = f"app_fox_previews{suffix}"
    if exists(target_directory) and adb("shell", "run-as", args.package, "ls", "-A", target_directory).stdout.strip():
        raise RuntimeError("Target preview directory contains files; refusing to overwrite it.")
    app_directory = adb("shell", "run-as", args.package, "pwd").stdout.decode().strip()
    original, scoped, images, target_before, fox_count = build_plan(
        args.owner_id, app_directory, lambda path: read(path) if exists(path) else None,
    )
    summary = dict(owner_id=args.owner_id, package=args.package, serial=args.serial,
                   preference_files=len(scoped), foxes=fox_count, preview_files=len(images),
                   empty_target_files=len(target_before), applied=False)
    if args.apply:
        args.backup_dir.mkdir(parents=True)
        for group, files in (("legacy", original), ("target_before", target_before)):
            for name, content in files.items():
                backup = args.backup_dir / group / name
                backup.parent.mkdir(parents=True, exist_ok=True)
                backup.write_bytes(content)
                if backup.read_bytes() != content:
                    raise RuntimeError(f"Backup verification failed: {backup}")
        summary["legacy_sha256"] = {p: hashlib.sha256(c).hexdigest() for p, c in original.items()}
        summary["target_sha256"] = {p: hashlib.sha256(c).hexdigest() for p, c in {**images, **scoped}.items()}
        (args.backup_dir / "migration.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
        adb("shell", "run-as", args.package, "mkdir", "-p", "files")
        write(ASSIGNMENT, json.dumps(summary).encode())
        if images:
            adb("shell", "run-as", args.package, "mkdir", "-p", target_directory)
            for path, content in images.items():
                write(path, content)
        for path, content in scoped.items():
            write(path, content)
        for path, content in original.items():
            if read(path) != content:
                raise RuntimeError(f"Original changed during migration: {path}")
        summary["applied"] = True
        write(ASSIGNMENT, json.dumps(summary).encode())
        (args.backup_dir / "migration.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary))


if __name__ == "__main__":
    main()
