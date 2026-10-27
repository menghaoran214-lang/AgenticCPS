#!/usr/bin/env python3
"""MySQL 8 syntax compatibility of module TEST fixtures; NOT production migrations."""
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import sys
import time

import pymysql

ROOT = Path(__file__).resolve().parents[2]
SOURCES = {
    "member": "backend/qiji-module-member/src/test/resources/sql/create_tables.sql",
    "product": "backend/qiji-module-mall/qiji-module-product/src/test/resources/sql/create_tables.sql",
    "trade": "backend/qiji-module-mall/qiji-module-trade/src/test/resources/sql/create_tables.sql",
    "promotion": "backend/qiji-module-mall/qiji-module-promotion/src/test/resources/sql/create_tables.sql",
    "pay": "backend/qiji-module-pay/src/test/resources/sql/create_tables.sql",
}


def convert_h2_test_ddl(sql):
    """A strictly test-only compatibility translation (not a supported migration)."""
    sql = re.sub(r'"([A-Za-z_][A-Za-z0-9_]*)"', r'`\1`', sql)
    sql = re.sub(r"\bGENERATED\s+BY\s+DEFAULT\s+AS\s+IDENTITY\b",
                 "AUTO_INCREMENT", sql, flags=re.I)
    sql = re.sub(r"\bvarchar\b(?!\s*\()", "varchar(255)", sql, flags=re.I)
    sql = re.sub(r"(?i)(\bbit(?:\(1\))?\s+NOT\s+NULL\s+DEFAULT\s*)'0'",
                 r"\g<1>0", sql)
    sql = re.sub(r"\bnumber\b", "bigint", sql, flags=re.I)
    sql = re.sub(r"\blong\b", "bigint", sql, flags=re.I)
    # Source's product fixture has malformed DROP TABLE ... (columns).
    sql = sql.replace("DROP TABLE IF EXISTS `product_comment` (",
                      "CREATE TABLE IF NOT EXISTS `product_comment` (")
    return sql


def run():
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("CPS_SCHEMA_PROBE_ISOLATED") != "1":
        raise RuntimeError("Refuse to run outside isolated GitHub Actions")
    host = os.environ.get("MYSQL_HOST", "127.0.0.1")
    if host not in ("127.0.0.1", "localhost"):
        raise RuntimeError("Database must be GitHub Actions loopback")
    db = "cps_schema_probe_" + secrets.token_hex(5)
    conn = None
    for attempt in range(45):
        try:
            conn = pymysql.connect(
                host=host, port=int(os.environ.get("MYSQL_PORT", "3306")),
                user="root", password=os.environ["MYSQL_ROOT_PASSWORD"],
                charset="utf8mb4", autocommit=True,
                connect_timeout=3,
            )
            break
        except pymysql.err.OperationalError:
            if attempt == 44:
                raise
            time.sleep(2)
    report = {"scope": "syntax only; NOT production-ready database schema",
              "temporary_database": db, "modules": {}, "failures": []}
    try:
        with conn.cursor() as cursor:
            cursor.execute("CREATE DATABASE `" + db + "` CHARACTER SET utf8mb4")
            cursor.execute("USE `" + db + "`")
            for module, source in SOURCES.items():
                raw = (ROOT / source).read_bytes()
                ddl = convert_h2_test_ddl(raw.decode("utf-8"))
                commands = [statement.strip() for statement in ddl.split(";") if statement.strip()]
                rows = {"source_sha256": hashlib.sha256(raw).hexdigest(),
                        "statements": len(commands), "result": "PASS"}
                try:
                    for command in commands:
                        cursor.execute(command)
                except Exception as exc:
                    rows["result"] = "FAIL"
                    rows["error"] = str(exc)[:300]
                    report["failures"].append(module)
                report["modules"][module] = rows
                print("SCHEMA_PROBE", module, rows["result"], flush=True)
            cursor.execute("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=%s", (db,))
            report["prototype_tables"] = cursor.fetchone()[0]
    finally:
        with conn.cursor() as cursor:
            cursor.execute("DROP DATABASE IF EXISTS `" + db + "`")
        conn.close()
        print("DISPOSABLE_SCHEMA_CLEANED", flush=True)
    out = ROOT / "schema-compatibility-report.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("PROTOTYPE_TABLES", report["prototype_tables"])
    if report["failures"] or report["prototype_tables"] != 41:
        raise RuntimeError("Test SQL compatibility check failed; production schema still unavailable")
    print("MODULE_TEST_SQL_SYNTAX_OK_41_TABLES")


if __name__ == "__main__":
    run()
