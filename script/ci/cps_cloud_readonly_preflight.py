#!/usr/bin/env python3
"""Read-only Tencent migration preflight. No arbitrary SQL, DDL, login or funds.
Prints aggregate metadata only; exit 2 means a migration/release gate is closed.
A valid backup checksum does not prove restore success or off-host durability.
"""
from datetime import datetime, timezone
import gzip
import hashlib
import json
from pathlib import Path
import re
import socket
import subprocess

ROOT = Path(__file__).resolve().parents[2]
BASE = Path("/home/cpsops/agenticcps")
CONTAINER = "agenticcps-qiji-mysql-1"
PROTECTED = ("cps_order", "cps_withdraw", "cps_rebate_asset_ledger",
             "member_user", "member_address", "member_tag", "member_level", "member_group")
QUERIES = {
    "identity": "SELECT VERSION(),DATABASE()",
    "columns": "SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE()",
    "accounts": "SELECT COUNT(*),SUM(status=0) FROM system_users",
    "indexes": "SELECT table_name,index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() ORDER BY table_name,index_name,seq_in_index",
}
QUERIES.update({name: "SELECT COUNT(*) FROM " + name for name in PROTECTED})

def validate_query(query):
    # Exact allowlist: future callers cannot turn this into a generic SQL bridge.
    if query not in QUERIES.values():
        raise ValueError("Only fixed aggregate inventory queries are permitted")
    return query

def command(args):
    result = subprocess.run(args, capture_output=True, text=True, timeout=25)
    if result.returncode:
        # Never echo stderr: database/daemon diagnostics may contain credentials.
        raise RuntimeError("Read-only command failed: " + args[0])
    return result.stdout.strip()

def query(name):
    sql = validate_query(QUERIES[name])
    return command(["docker", "exec", CONTAINER, "sh", "-c",
        'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" -D"$MYSQL_DATABASE" --batch --skip-column-names -e "$1"',
        "preflight", sql])

def expected_columns():
    expected = {}
    directory = ROOT / "backend/sql/migrations"
    for path in sorted(directory.glob("V20261028_0[1-5]__*.sql")):
        text = path.read_text(encoding="utf-8")
        for table, body in re.findall(r"CREATE TABLE `([a-z_]+)`\s*\((.*?)\) ENGINE=", text, re.S):
            if table in expected:
                raise ValueError("Duplicate candidate table")
            expected[table] = set(re.findall(r"^\s*`([a-z_]+)`\s+", body, re.M))
    if len(expected) != 67:
        raise ValueError("Expected reviewed 67-table migration inventory")
    correction = directory / "V20261028_06__member_tenant_interceptor_alignment.sql"
    for table, column in re.findall(r"ALTER TABLE `([a-z_]+)`\s+ADD COLUMN `([a-z_]+)`", correction.read_text(encoding="utf-8")):
        expected[table].add(column)
    expected["member_user"].add("active_mobile")
    return expected

def expected_indexes():
    indexes = {}
    for path in sorted((ROOT / "backend/sql/migrations").glob("V20261028_0[1-5]__*.sql")):
        for table, body in re.findall(r"CREATE TABLE \x60([a-z_]+)\x60\s*\((.*?)\) ENGINE=", path.read_text(encoding="utf-8"), re.S):
            for unique, name, columns in re.findall(r"^\s*(UNIQUE )?KEY \x60([a-z_]+)\x60 \(([^)]+)\)", body, re.M):
                indexes[(table, name)] = (0 if unique else 1, tuple(re.findall(r"\x60([a-z_]+)\x60", columns)))
    correction = (ROOT / "backend/sql/migrations/V20261028_06__member_tenant_interceptor_alignment.sql").read_text(encoding="utf-8")
    for table, name, columns in re.findall(r"ALTER TABLE \x60([a-z_]+)\x60.*?ADD KEY \x60([a-z_]+)\x60 \(([^)]+)\)", correction, re.S):
        indexes[(table, name)] = (1, tuple(re.findall(r"\x60([a-z_]+)\x60", columns)))
    indexes.pop(("member_user", "uk_member_tenant_mobile_deleted"))
    indexes[("member_user", "uk_member_tenant_active_mobile")] = (0, ("tenant_id", "active_mobile"))
    return indexes

def schema_gaps(expected, actual):
    return {"missing_tables": sorted(set(expected) - set(actual)),
            "missing_columns": {table: sorted(columns - actual[table])
                for table, columns in sorted(expected.items())
                if table in actual and columns - actual[table]}}

def empty_gate(counts):
    return bool(counts) and all(type(value) is int and value == 0 for value in counts.values())

def backup_check(directory):
    candidates = sorted(directory.glob("scheduled-*.sql.gz"),
                        key=lambda path: path.stat().st_mtime, reverse=True)
    if not candidates:
        return {"verified": False, "reason": "no scheduled archive"}
    path = candidates[0]
    sidecar = path.with_name(path.name + ".sha256")
    if path.is_symlink() or sidecar.is_symlink() or not sidecar.is_file():
        return {"verified": False, "reason": "missing or unsafe checksum"}
    recorded = sidecar.read_text(encoding="utf-8").split()
    if not recorded or not re.fullmatch("[a-fA-F0-9]{64}", recorded[0]):
        return {"verified": False, "reason": "invalid checksum"}
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    if digest.hexdigest() != recorded[0].lower():
        return {"verified": False, "reason": "checksum mismatch"}
    try:
        with gzip.open(path, "rb") as stream:
            while stream.read(1024 * 1024):
                pass
    except (OSError, EOFError):
        return {"verified": False, "reason": "invalid gzip"}
    return {"verified": True, "archive": path.name,
            "sha256": digest.hexdigest(), "restore_verified": False,
            "off_host_verified": False}

def main():
    if socket.gethostname() != "VM-0-8-ubuntu" or command(["id", "-un"]) != "cpsops":
        raise RuntimeError("Only the explicitly approved Tencent cpsops host is supported")
    identity = query("identity").split("\t")
    if len(identity) != 2 or not identity[0].startswith("8.0.") or identity[1] != "cps":
        raise RuntimeError("Unexpected MySQL identity")
    actual = {}
    for line in query("columns").splitlines():
        table, column = line.split("\t")
        actual.setdefault(table, set()).add(column)
    expected = expected_columns()
    gaps = schema_gaps(expected, actual)
    actual_indexes = {}
    for line in query("indexes").splitlines():
        table, name, non_unique, sequence, column = line.split("\t")
        actual_indexes.setdefault((table, name), (int(non_unique), []))[1].append(column)
    mismatched_indexes = []
    for (table, name), (non_unique, columns) in expected_indexes().items():
        if table not in actual:
            continue
        found = actual_indexes.get((table, name))
        if not found or found[0] != non_unique or tuple(found[1]) != columns:
            mismatched_indexes.append(table + "." + name)
    gaps["missing_or_different_indexes"] = sorted(mismatched_indexes)
    counts = {name: int(query(name)) if name in actual else None for name in PROTECTED}
    accounts = query("accounts").split("\t")
    containers = command(["docker", "ps", "--format", "{{.Names}}"]).splitlines()
    ports = command(["ss", "-H", "-lnt"])
    listeners = [line.split()[3] for line in ports.splitlines()]
    no_db_ports = not any(re.search(r":(?:3306|6379|48080)$", address) for address in listeners)
    web_local = "127.0.0.1:18080" in listeners and all(
        address == "127.0.0.1:18080" for address in listeners if address.endswith(":18080"))
    backup = backup_check(BASE / "backups")
    manifest = json.loads((BASE / "runtime/artifact-manifest.json").read_text(encoding="utf-8"))
    head = command(["git", "-C", str(ROOT), "rev-parse", "HEAD"])
    checks = {
        "all_candidate_columns_deployed": not gaps["missing_tables"] and not gaps["missing_columns"],
        "existing_table_candidate_indexes_match": not mismatched_indexes,
        "protected_tables_empty": empty_gate(counts),
        "bootstrap_accounts_reduced": int(accounts[0]) <= 1,
        "backup_checksum_and_gzip_valid": backup["verified"],
        "database_and_api_ports_unpublished": no_db_ports,
        "web_localhost_only": web_local,
        "four_project_containers_running": all("agenticcps-" + name + "-1" in containers
            for name in ("qiji-mysql", "qiji-redis", "qiji-server", "admin")),
        "runtime_manifest_matches_reviewed_commit": manifest.get("code_commit") == head,
    }
    report = {"utc": datetime.now(timezone.utc).isoformat(),
        "scope": "read-only current Tencent inventory; no migration or credentials tested",
        "reviewed_commit": head, "runtime_manifest_commit": manifest.get("code_commit"),
        "mysql_version": identity[0], "table_count": len(actual),
        "schema_gaps": gaps, "aggregate_row_counts": counts,
        "bootstrap_accounts": int(accounts[0]), "enabled_accounts": int(accounts[1]),
        "backup": backup, "checks": checks,
        "candidate_sha256": {path.name: hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sorted((ROOT / "backend/sql/migrations").glob("V20261028_*.sql"))},
        "member_update_sha256": hashlib.sha256((ROOT / "backend/sql/module/member-update.sql").read_bytes()).hexdigest(),
        "cloud_restore_rehearsal_verified": False, "positive_login_verified": False,
        "financial_e2e_verified": False, "go_live_approved": False}
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 2 if not all(checks.values()) else 0

if __name__ == "__main__":
    raise SystemExit(main())
