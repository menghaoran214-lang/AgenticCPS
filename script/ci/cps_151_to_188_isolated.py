#!/usr/bin/env python3
"""Rehearse the source-controlled 151 -> 188 CPS migration in a disposable CI MySQL 8.

This is NOT a Tencent backup restore, a production migration, a credential test,
or a release approval. No network endpoint beyond the local CI MySQL is accepted.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import secrets

from pymysql.err import IntegrityError

from cps_full_baseline_integration import (
    ADDITIONS, BASELINE, ROOT, connect, run_sql_file, table_names,
)

BT = chr(96)
MIGRATIONS = ROOT / "backend/sql/migrations"
PROTECTED = ("cps_order", "cps_withdraw", "cps_rebate_asset_ledger", "member_user")
TENANT_FIX = ("member_address", "member_tag", "member_level", "member_group")
MEMBER_UPDATE = ROOT / "backend/sql/module/member-update.sql"


def guard():
    required = {
        "GITHUB_ACTIONS": "true",
        "RUNNER_ENVIRONMENT": "github-hosted",
        "CPS_FULL_BASELINE_CI": "1",
        "CPS_151_TO_188_ISOLATED": "1",
        "MYSQL_ROOT_PASSWORD": "local-github-runner-test-only",
    }
    if any(os.environ.get(key) != val for key, val in required.items()):
        raise RuntimeError("Only the designated disposable GitHub-hosted MySQL job is allowed")
    if os.environ.get("MYSQL_HOST") not in ("127.0.0.1", "localhost"):
        raise RuntimeError("Refuse non-loopback databases")
    if os.environ.get("MYSQL_PORT", "3306") != "3306":
        raise RuntimeError("Refuse unexpected MySQL port")
    if any(not path.is_file() for _, path in BASELINE) or not MEMBER_UPDATE.is_file():
        raise RuntimeError("Source-controlled migration inputs are missing")


def statements(path):
    source = path.read_text(encoding="utf-8-sig")
    # All inputs are fixed repository paths; fail closed on unexpected SQL directives.
    if re.search(r"(?im)^\s*(?:USE|CREATE DATABASE|DROP DATABASE|DELIMITER)\b", source):
        raise ValueError("Unexpected database redirection or client directive")
    source = re.sub(r"(?m)^\s*--[^\n]*$", "", source)
    # Preserve semicolons inside quoted SQL values (e.g. schema-version descriptions).
    parts, current, quote = [], [], None
    i = 0
    while i < len(source):
        ch = source[i]
        if quote is not None:
            current.append(ch)
            if ch == "\\" and i + 1 < len(source):
                i += 1
                current.append(source[i])
            elif ch == quote:
                if i + 1 < len(source) and source[i + 1] == quote:
                    i += 1
                    current.append(source[i])
                else:
                    quote = None
        elif ch in ("'", '"', BT):
            quote = ch
            current.append(ch)
        elif ch == ";":
            command = "".join(current).strip()
            if command:
                parts.append(command)
            current = []
        else:
            current.append(ch)
        i += 1
    if quote is not None:
        raise ValueError("Unterminated quoted SQL value")
    trailing = "".join(current).strip()
    if trailing:
        parts.append(trailing)
    return parts


def rows(cursor, table):
    if not re.fullmatch(r"[A-Za-z0-9_]+", table):
        raise ValueError("Invalid table identifier")
    cursor.execute("SELECT * FROM " + BT + table + BT)
    return sorted(repr(row) for row in cursor.fetchall())


def fingerprints(cursor, names):
    result = {}
    for table in sorted(names):
        items = rows(cursor, table)
        result[table] = (len(items), hashlib.sha256(
            "\n".join(items).encode("utf-8")).hexdigest())
    return result


def schemas(cursor, names):
    result = {}
    for table in sorted(names):
        cursor.execute("SHOW CREATE TABLE " + BT + table + BT)
        result[table] = cursor.fetchone()[1]
    return result


def apply_new_tables(cursor, module_labels, original):
    existing = set(original)
    created = set()
    for label, filename, expected in ADDITIONS:
        if label not in module_labels:
            continue
        path = MIGRATIONS / filename
        sql = statements(path)
        if len(sql) != expected:
            raise AssertionError("Unexpected DDL count in " + filename)
        names = []
        for command in sql:
            match = re.match(r"^CREATE TABLE " + BT + r"([a-z_]+)" + BT + r"\s*\(", command)
            if not match or not match[1].startswith(label + "_"):
                raise AssertionError("Unexpected DDL command for " + label)
            name = match[1]
            if name in existing or name in created:
                raise AssertionError("Migration would recreate an existing table: " + name)
            names.append(name)
            created.add(name)
        # Verify the entire module before issuing any DDL in this module.
        for command in sql:
            cursor.execute(command)
    return created


def require_empty_member_fix(cursor):
    for name in TENANT_FIX:
        cursor.execute("SELECT COUNT(*) FROM " + BT + name + BT)
        if cursor.fetchone()[0] != 0:
            raise RuntimeError("V06 blocked: member tenant ownership requires manual review")


def test_nonempty_member_gate(cursor):
    # CI-only synthetic row: a migration must reject unknown existing tenant ownership.
    require_empty_member_fix(cursor)
    cursor.execute("INSERT INTO member_tag (name) VALUES ('isolated-gate-probe')")
    row_id = cursor.lastrowid
    blocked = False
    try:
        try:
            require_empty_member_fix(cursor)
        except RuntimeError:
            blocked = True
    finally:
        cursor.execute("DELETE FROM member_tag WHERE id=%s", (row_id,))
    require_empty_member_fix(cursor)
    if not blocked:
        raise AssertionError("V06 nonempty-history guard failed")
    return True


def apply_v06_and_member_v201(cursor):
    require_empty_member_fix(cursor)
    correction_path = MIGRATIONS / "V20261028_06__member_tenant_interceptor_alignment.sql"
    fix = statements(correction_path)
    if len(fix) != 4 or any(
        not re.match(r"^ALTER TABLE " + BT + r"member_(?:address|tag|level|group)" + BT, sql)
        for sql in fix
    ):
        raise AssertionError("Unexpected V06 statements")
    expected = ["member_address", "member_tag", "member_level", "member_group"]
    for table, command in zip(expected, fix):
        if not command.startswith("ALTER TABLE " + BT + table + BT + "\n"):
            raise AssertionError("Unexpected V06 target or order")
        cursor.execute(command)
    update = statements(MEMBER_UPDATE)
    if len(update) != 2 or not update[0].startswith("ALTER TABLE " + BT + "member_user" + BT):
        raise AssertionError("Unexpected member v2.0.1 migration")
    if not update[1].startswith("INSERT INTO " + BT + "system_schema_version" + BT):
        raise AssertionError("Unexpected member version record")
    cursor.execute(update[0])
    cursor.execute(update[1])


def verify_active_mobile(cursor):
    mobile = "13900000001"  # Synthetic-only; never send OTP or create a real account.
    for _ in range(2):
        cursor.execute("INSERT INTO member_user (tenant_id,mobile) VALUES (1,%s)", (mobile,))
        first_id = cursor.lastrowid
        cursor.execute("UPDATE member_user SET deleted=b'1' WHERE id=%s", (first_id,))
    cursor.execute("INSERT INTO member_user (tenant_id,mobile) VALUES (1,%s)", (mobile,))
    try:
        cursor.execute("INSERT INTO member_user (tenant_id,mobile) VALUES (1,%s)", (mobile,))
    except IntegrityError:
        duplicate_rejected = True
    else:
        duplicate_rejected = False
    cursor.execute("SELECT COUNT(*) FROM member_user WHERE tenant_id=1 AND mobile=%s", (mobile,))
    history_count = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(*) FROM member_user WHERE tenant_id=1 AND active_mobile=%s", (mobile,))
    active_count = cursor.fetchone()[0]
    return duplicate_rejected and history_count == 3 and active_count == 1


def main():
    guard()
    name = "cps_151_to_188_ci_" + secrets.token_hex(6)
    if not re.fullmatch(r"cps_151_to_188_ci_[0-9a-f]{12}", name):
        raise AssertionError("Invalid disposable database name")
    conn = connect()
    created_db = False
    checks = {}
    report = {
        "scope": "disposable GitHub-hosted MySQL 8 built from repository SQL, not cloud backup",
        "original_cloud_data_read": False,
        "cloud_backup_restored": False,
        "actual_cloud_schema_compared": False,
        "live_order_or_payment_executed": False,
        "go_live_approved": False,
        "source_sha256": {},
        "checks": checks,
    }
    try:
        with conn.cursor() as cur:
            cur.execute("CREATE DATABASE " + BT + name + BT + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
            created_db = True
            cur.execute("USE " + BT + name + BT)
            for _, path in BASELINE:
                run_sql_file(cur, path)
            base = table_names(cur)
            checks["canonical_baseline_is_121"] = len(base) == 121
            if not checks["canonical_baseline_is_121"]:
                raise AssertionError("Unexpected baseline schema")
            added_30 = apply_new_tables(cur, ("member", "product", "trade"), base)
            before = table_names(cur)
            checks["synthetic_151_baseline"] = len(before) == 151 and len(added_30) == 30
            if not checks["synthetic_151_baseline"]:
                raise AssertionError("Expected exactly 151 tables before migration")
            checks["member_ownership_nonempty_gate"] = test_nonempty_member_gate(cur)
            for name_protected in PROTECTED:
                if rows(cur, name_protected):
                    raise AssertionError("Synthetic baseline has unexpected business records")
            baseline_fingerprints = fingerprints(cur, before)
            baseline_schemas = schemas(cur, before)
            created_37 = apply_new_tables(cur, ("promotion", "pay"), before)
            checks["added_37_new_module_tables"] = len(created_37) == 37
            if not checks["added_37_new_module_tables"]:
                raise AssertionError("Promotion / payment table inventory mismatch")
            apply_v06_and_member_v201(cur)
            after = table_names(cur)
            checks["188_tables_no_drop"] = len(after) == 188 and after == before | created_37
            after_fingerprints = fingerprints(cur, before)
            checks["all_original_rows_preserved"] = all(
                after_fingerprints[t] == baseline_fingerprints[t]
                for t in before if t != "system_schema_version"
            )
            changed_schema = set(TENANT_FIX) | {"member_user"}
            after_schemas = schemas(cur, before)
            checks["other_146_schemas_unchanged"] = all(
                after_schemas[t] == baseline_schemas[t]
                for t in before - changed_schema
            )
            for table in TENANT_FIX:
                cur.execute("SHOW COLUMNS FROM " + BT + table + BT + " LIKE 'tenant_id'")
                checks["tenant_column_" + table] = cur.fetchone() is not None
            cur.execute("SHOW INDEX FROM member_user WHERE Key_name='uk_member_tenant_active_mobile'")
            checks["active_mobile_unique_index_exists"] = cur.fetchone() is not None
            cur.execute("SHOW INDEX FROM member_user WHERE Key_name='uk_member_tenant_mobile_deleted'")
            checks["obsolete_member_index_removed"] = cur.fetchone() is None
            for table in ("pay_app", "pay_channel"):
                cur.execute("SELECT column_default FROM information_schema.columns WHERE table_schema=%s AND table_name=%s AND column_name='status'", (name, table))
                default = cur.fetchone()
                checks["pay_default_disabled_" + table] = default is not None and str(default[0]) == "1"
                cur.execute("SELECT COUNT(*) FROM " + BT + table + BT)
                checks["no_seeded_" + table] = cur.fetchone()[0] == 0
            try:
                apply_new_tables(cur, ("promotion", "pay"), after)
            except AssertionError:
                checks["replay_refused_before_ddl"] = True
            else:
                checks["replay_refused_before_ddl"] = False
            checks["member_repeated_delete_recreate"] = verify_active_mobile(cur)
            report["before_tables"] = len(before)
            report["after_tables"] = len(after)
            report["original_tables_fingerprinted"] = len(before)
            report["immutable_schema_count"] = len(before - changed_schema)
            for _, path in BASELINE:
                report["source_sha256"][str(path.relative_to(ROOT))] = hashlib.sha256(path.read_bytes()).hexdigest()
            for _, filename, _ in ADDITIONS:
                path = MIGRATIONS / filename
                report["source_sha256"][str(path.relative_to(ROOT))] = hashlib.sha256(path.read_bytes()).hexdigest()
            for path in (MIGRATIONS / "V20261028_06__member_tenant_interceptor_alignment.sql", MEMBER_UPDATE):
                report["source_sha256"][str(path.relative_to(ROOT))] = hashlib.sha256(path.read_bytes()).hexdigest()
    finally:
        if created_db:
            with conn.cursor() as cur:
                cur.execute("DROP DATABASE IF EXISTS " + BT + name + BT)
            print("ISOLATED_151_TO_188_DATABASE_REMOVED=YES", flush=True)
        conn.close()
    report["passed"] = bool(checks) and all(checks.values())
    (ROOT / "cps-151-to-188-ci-report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("151_TO_188_SCHEMA_GATE", json.dumps(checks, sort_keys=True), flush=True)
    if not report["passed"]:
        raise SystemExit("151->188 synthetic schema migration gate failed")
    print("ISOLATED_151_TO_188_PASS", flush=True)


if __name__ == "__main__":
    main()
