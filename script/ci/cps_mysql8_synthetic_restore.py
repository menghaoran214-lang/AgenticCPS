#!/usr/bin/env python3
"""Synthetic CI-only MySQL dump/restore rehearsal. Never accepts a host or backup path."""
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

SOURCE = "cps_mapper_ci"
TARGET = "cps_restore_ci"

def guard():
    if (os.environ.get("GITHUB_ACTIONS") != "true"
            or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted"
            or os.environ.get("CPS_MYSQL8_INTEGRATION") != "1"):
        raise RuntimeError("Only the disposable GitHub-hosted mapper test job is supported")

def command(program, args, data=None):
    env = dict(os.environ, MYSQL_PWD="mapper-ci-only")
    result = subprocess.run([program, "-h127.0.0.1", "-uroot", *args],
                            input=data, capture_output=True, env=env, timeout=90)
    if result.returncode:
        raise RuntimeError("Synthetic CI database command failed: " + program)
    return result.stdout

def sql(database, statement):
    if database not in (SOURCE, TARGET):
        raise ValueError("Unexpected schema")
    return command("mysql", ["--batch", "--raw", "--skip-column-names", database,
                             "-e", statement])

def fingerprint(database):
    tables = sql(database, "SHOW TABLES").decode().splitlines()
    assert tables and all(re.fullmatch("[a-z_]+", table) for table in tables)
    return {table: {
        "rows": int(sql(database, "SELECT COUNT(*) FROM `" + table + "`")),
        "data_sha256": hashlib.sha256(sql(database, "SELECT * FROM `" + table + "` ORDER BY id")).hexdigest(),
    } for table in sorted(tables)}

def main():
    guard()
    assert sql(SOURCE, "SELECT VERSION()").decode().startswith("8.0.")
    before = fingerprint(SOURCE)
    # No --databases: dump must not carry a source CREATE DATABASE/USE target.
    dump = command("mysqldump", ["--single-transaction", "--no-tablespaces",
                   "--set-gtid-purged=OFF", "--skip-comments", "--skip-dump-date", SOURCE])
    if re.search(rb"(?im)^\\s*(?:CREATE DATABASE|USE\\s)", dump):
        raise RuntimeError("Dump contains a schema redirection")
    with tempfile.TemporaryDirectory(prefix="cps-synthetic-restore-") as folder:
        archive = Path(folder) / "synthetic.sql.gz"
        archive.write_bytes(gzip.compress(dump, mtime=0))
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        assert gzip.decompress(archive.read_bytes()) == dump
        # TARGET is owned exclusively by this fresh Actions service.
        command("mysql", ["-e", "CREATE DATABASE cps_restore_ci CHARACTER SET utf8mb4"])
        try:
            command("mysql", [TARGET], gzip.decompress(archive.read_bytes()))
            after = fingerprint(TARGET)
            assert after == before, "Synthetic row fingerprints differ after restore"
            assert fingerprint(SOURCE) == before, "Source data changed during rehearsal"
            assert hashlib.sha256(archive.read_bytes()).hexdigest() == digest
            report = {"scope": "synthetic GitHub-hosted data only",
                      "mysql8_dump_restore_verified": True,
                      "source_unchanged": True, "table_count": len(before),
                      "row_counts": {key: value["rows"] for key, value in before.items()},
                      "archive_sha256": digest,
                      "tencent_backup_restore_verified": False,
                      "off_host_backup_verified": False}
            Path("mysql8-synthetic-restore-report.json").write_text(
                json.dumps(report, indent=2), encoding="utf-8")
            print(json.dumps(report))
        finally:
            command("mysql", ["-e", "DROP DATABASE cps_restore_ci"])

if __name__ == "__main__":
    main()
