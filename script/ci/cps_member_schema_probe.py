#!/usr/bin/env python3
"""MySQL 8 member migration preflight against disposable GitHub Actions DB only."""
import json
import os
from pathlib import Path
import re
import secrets
import time

import pymysql
from pymysql.err import IntegrityError

ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "backend/sql/migrations/V20261028_01__member_core_mysql8.sql"
TABLES = {
    "member_user", "member_address", "member_tag", "member_level",
    "member_group", "member_brokerage_record"
}


def main():
    if os.getenv("GITHUB_ACTIONS") != "true" or os.getenv("CPS_MEMBER_SCHEMA_ISOLATED") != "1":
        raise RuntimeError("Refusing schema mutation outside isolated GitHub Actions")
    if os.getenv("MYSQL_HOST") not in ("localhost", "127.0.0.1"):
        raise RuntimeError("CI database must be loopback")
    conn = None
    for attempt in range(30):
        try:
            conn = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                password=os.environ["MYSQL_ROOT_PASSWORD"], charset="utf8mb4",
                autocommit=True, connect_timeout=3)
            break
        except pymysql.err.OperationalError:
            if attempt == 29:
                raise
            time.sleep(2)
    db = "cps_member_schema_ci_" + secrets.token_hex(5)
    checks = {}
    try:
        with conn.cursor() as cur:
            cur.execute("CREATE DATABASE `" + db + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
            cur.execute("USE `" + db + "`")
            # This SQL file contains only CREATE TABLE declarations.
            source = re.sub(r"(?m)^--.*$", "", MIGRATION.read_text(encoding="utf-8"))
            statements = [part.strip() for part in source.split(";") if part.strip()]
            if len(statements) != 6 or any(not item.startswith("CREATE TABLE") for item in statements):
                raise AssertionError("Unexpected migration statements")
            for statement in statements:
                cur.execute(statement)
            cur.execute("SHOW TABLES")
            actual = {x[0] for x in cur.fetchall()}
            checks["expected_tables"] = actual == TABLES
            cur.execute("SHOW COLUMNS FROM member_user")
            columns = {row[0] for row in cur.fetchall()}
            checks["login_columns"] = {"id","mobile","password","status","register_terminal",
                "tenant_id","deleted","create_time","tag_ids"}.issubset(columns)
            cur.execute("INSERT INTO member_user(mobile,password,tenant_id,status) VALUES "
                        "('19990001111','test-hash',1,0)")
            duplicate_rejected = False
            try:
                cur.execute("INSERT INTO member_user(mobile,password,tenant_id,status) VALUES "
                            "('19990001111','test-hash',1,0)")
            except IntegrityError:
                duplicate_rejected = True
            checks["duplicate_rejected"] = duplicate_rejected
            cur.execute("INSERT INTO member_user(mobile,password,tenant_id,status) VALUES "
                        "('19990001111','test-hash',2,0)")
            cur.execute("SELECT COUNT(*) FROM member_user")
            checks["same_mobile_cross_tenant"] = cur.fetchone()[0] == 2
            cur.execute("UPDATE member_user SET deleted=b'1' WHERE tenant_id=1")
            cur.execute("INSERT INTO member_user(mobile,password,tenant_id,status) VALUES "
                        "('19990001111','test-hash',1,0)")
            cur.execute("SELECT COUNT(*) FROM member_user")
            checks["soft_delete_reregistration"] = cur.fetchone()[0] == 3
    finally:
        with conn.cursor() as cur:
            cur.execute("DROP DATABASE IF EXISTS `" + db + "`")
        conn.close()
        print("CI_ISOLATED_DB_DROPPED")
    report = {"scope": "isolated member schema verification, never production",
              "checks": checks, "passed": all(checks.values())}
    (ROOT / "member-schema-ci-report.json").write_text(json.dumps(report, indent=2) + "\n")
    print("CI_MEMBER_SCHEMA_CHECKS", json.dumps(checks, sort_keys=True))
    if not report["passed"]:
        raise SystemExit("Member schema constraints failed")


if __name__ == "__main__":
    main()
