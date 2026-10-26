#!/usr/bin/env python3
"""Inspect tracked text source files for invalid UTF-8, BOM and replacement chars."""
from __future__ import annotations
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXT = {".py",".java",".js",".jsx",".ts",".tsx",".vue",".json",".yaml",".yml",".xml",".md",".txt",".sql",".sh",".bash",".properties",".html",".htm",".css",".scss",".less",".toml",".gradle"}
NAMES = {"Dockerfile","Makefile","Jenkinsfile","AGENTS.md"}
SKIP = {".git","node_modules","dist","target","unpackage","vendor",".venv"}
MAX_BYTES = 12 * 1024 * 1024

def check_bytes(path: str, data: bytes) -> list[str]:
    errors = []
    if data.startswith(b"\xef\xbb\xbf"):
        errors.append(f"{path}: unexpected UTF-8 BOM")
    try:
        text = data.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        errors.append(f"{path}: invalid UTF-8 at byte {exc.start}")
        return errors
    if "\ufffd" in text:
        errors.append(f"{path}: Unicode replacement character U+FFFD")
    return errors

def main() -> int:
    files = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    errors, count = [], 0
    for part in files.split(b"\x00"):
        if not part:
            continue
        rel = Path(part.decode("utf-8", "surrogateescape"))
        if any(name in SKIP for name in rel.parts):
            continue
        if rel.name not in NAMES and rel.suffix.lower() not in EXT:
            continue
        path = ROOT / rel
        if not path.is_file():
            continue
        if path.stat().st_size > MAX_BYTES:
            errors.append(f"{rel}: text source larger than {MAX_BYTES} bytes")
            continue
        count += 1
        errors.extend(check_bytes(str(rel), path.read_bytes()))
    if errors:
        print("\n".join(errors[:100]), file=sys.stderr)
        print(f"FAIL: {len(errors)} file encoding issues in {count} checked text files")
        return 1
    print(f"PASS: {count} tracked text files are UTF-8")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
