#!/usr/bin/env python3
"""Rebuild the repository baseline + all five CPS module schemas in disposable CI MySQL.

Never accesses the Tencent cloud database, external payment providers, or live funds.
A passing result is schema-compatibility evidence only, not go-live approval.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import time

import pymysql
from pymysql.constants import CLIENT

ROOT=Path(__file__).resolve().parents[2]
BT=chr(96)
BASELINE=(
    ("main",ROOT/"backend/sql/mysql/ruoyi-vue-pro.sql"),
    ("quartz",ROOT/"backend/sql/mysql/quartz.sql"),
    ("cps",ROOT/"backend/sql/module/cps-all-in-one.sql"),
)
ADDITIONS=(
    ("member","V20261028_01__member_core_mysql8.sql",6),
    ("product","V20261028_02__product_core_mysql8.sql",9),
    ("trade","V20261028_03__trade_core_mysql8.sql",15),
    ("promotion","V20261028_04__promotion_core_mysql8.sql",23),
    ("pay","V20261028_05__pay_core_mysql8.sql",14),
)
CHECK_EXISTING={
    "system_users","system_tenant","system_oauth2_access_token",
    "cps_order","cps_withdraw","cps_rebate_asset_ledger"
}

def check_environment():
    if os.getenv("GITHUB_ACTIONS")!="true" or os.getenv("CPS_FULL_BASELINE_CI")!="1":
        raise RuntimeError("Refuse to run outside isolated GitHub Actions")
    if os.getenv("MYSQL_HOST") not in ("127.0.0.1","localhost"):
        raise RuntimeError("Refuse all remote MySQL connections")
    if not all(path.is_file() for _,path in BASELINE):
        raise RuntimeError("Missing source-controlled database bootstrap SQL")

def connect():
    for attempt in range(40):
        try:
            return pymysql.connect(
                host="127.0.0.1", port=3306, user="root",
                password=os.environ["MYSQL_ROOT_PASSWORD"],
                autocommit=True, charset="utf8mb4",
                connect_timeout=3, client_flag=CLIENT.MULTI_STATEMENTS
            )
        except pymysql.err.OperationalError:
            if attempt==39:raise
            time.sleep(2)

def run_sql_file(cursor,path):
    raw=path.read_text(encoding="utf-8-sig")
    if re.search(r"(?im)^\s*(CREATE\s+DATABASE|DROP\s+DATABASE|USE\s+)",raw):
        raise RuntimeError("Bootstrap SQL cannot switch database: "+path.name)
    if re.search(r"(?im)^\s*DELIMITER\b",raw):
        raise RuntimeError("DELIMITER directive needs explicit review")
    cursor.execute(raw)
    statement_sets=1
    while cursor.nextset():
        statement_sets+=1
    return statement_sets

def table_names(cursor):
    cursor.execute("SHOW TABLES")
    return {row[0] for row in cursor.fetchall()}

def table_rows(cursor, table):
    cursor.execute("SELECT COUNT(*) FROM "+BT+table+BT)
    return cursor.fetchone()[0]

def tenant_history_safe(counts):
    """Never infer default tenant 1 for preexisting member rows."""
    names = ("member_address", "member_tag", "member_level", "member_group")
    return set(counts) == set(names) and all(type(counts[t]) is int and counts[t] == 0 for t in names)


def main():
    check_environment()
    db="agenticcps_full_baseline_ci_"+secrets.token_hex(5)
    assert re.fullmatch(r"agenticcps_full_baseline_ci_[0-9a-f]{10}",db)
    conn=connect()
    report={
        "scope":"Fresh, disposable GitHub Actions MySQL 8 database",
        "source_files":{},
        "checks":{},
        "real_credentials_used":False,
        "real_payment_executed":False,
        "go_live_approved":False,
    }
    checks=report["checks"]
    created=False
    try:
        with conn.cursor() as cur:
            cur.execute("CREATE DATABASE "+BT+db+BT+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
            created=True
            cur.execute("USE "+BT+db+BT)
            # Three canonical files used to initialize the real cloud database.
            for label,path in BASELINE:
                script=path.read_bytes()
                report["source_files"][path.as_posix().removeprefix(ROOT.as_posix()+"/")]=hashlib.sha256(script).hexdigest()
                statements=run_sql_file(cur,path)
                print("BASELINE_IMPORTED",label,"result_sets",statements,flush=True)
            base=table_names(cur)
            report["baseline_table_count"]=len(base)
            checks["baseline_core_and_cps_tables"]=len(base)>=100 and CHECK_EXISTING.issubset(base)
            if not checks["baseline_core_and_cps_tables"]:
                raise AssertionError("Baseline missing essential CPS or security tables")
            # Snapshot identities/row counts before adding module schemas.
            immutable_counts={name:table_rows(cur,name) for name in CHECK_EXISTING}
            names_expected=set()
            staged_151=set()
            stage_151_seed_rows={}
            for module,file,count in ADDITIONS:
                path=ROOT/"backend/sql/migrations"/file
                blob=path.read_bytes()
                report["source_files"][path.as_posix().removeprefix(ROOT.as_posix()+"/")]=hashlib.sha256(blob).hexdigest()
                ddl=re.sub(r"(?m)^--.*$","",blob.decode("utf8"))
                statements=[s.strip() for s in ddl.split(";") if s.strip()]
                if len(statements)!=count:
                    raise AssertionError("Unexpected module DDL statements: "+module)
                for stmt in statements:
                    match=re.match(r"^CREATE TABLE "+BT+r"([a-z_]+)"+BT,stmt)
                    if not match or not match[1].startswith(module+"_"):
                        raise AssertionError("Unsafe or unexpected DDL in "+file)
                    if match[1] in base or match[1] in names_expected:
                        raise AssertionError("Table already exists in baseline: "+match[1])
                    names_expected.add(match[1])
                    cur.execute(stmt)
                print("MIGRATION_IMPORTED",module,count,flush=True)
                if module == "trade":
                    # Model the actual cloud state: canonical 121 objects followed by
                    # member 6, product 9, and trade 15, before promotion/pay additions.
                    staged_151=table_names(cur)
                    report["cloud_equivalent_stage_objects"]=len(staged_151)
                    checks["121_to_151_phase_boundary"]=(
                        len(base)==121 and len(staged_151)==151
                        and len(staged_151-base)==30
                        and (staged_151-base)==names_expected)
                    cur.execute(
                        "SELECT table_type, COUNT(*) FROM information_schema.tables "
                        "WHERE table_schema=%s GROUP BY table_type", (db,))
                    stage_types=dict(cur.fetchall())
                    checks["cloud_equivalent_150_tables_one_view"]=(
                        stage_types.get("BASE TABLE")==150
                        and stage_types.get("VIEW")==1)
                    stage_151_seed_rows={name:table_rows(cur,name) for name in CHECK_EXISTING}
                    checks["original_seed_rows_unchanged_at_151"]=stage_151_seed_rows==immutable_counts
                    member_counts={name:table_rows(cur,name) for name in
                                   ("member_address","member_tag","member_level","member_group")}
                    checks["tenant_alignment_history_empty_at_151"]=tenant_history_safe(member_counts)
                    checks["nonempty_tenant_history_fails_closed"]=not tenant_history_safe({
                        "member_address":1, "member_tag":0, "member_level":0, "member_group":0})
                    if not all(checks[k] for k in (
                        "121_to_151_phase_boundary",
                        "cloud_equivalent_150_tables_one_view",
                        "original_seed_rows_unchanged_at_151",
                        "tenant_alignment_history_empty_at_151",
                        "nonempty_tenant_history_fails_closed",
                    )):
                        raise AssertionError("Simulated 151-object cloud upgrade gate rejected")
                    print("CLOUD_EQUIVALENT_151_OBJECT_STAGE_CHECKED=YES",flush=True)
            correction=ROOT/"backend/sql/migrations/V20261028_06__member_tenant_interceptor_alignment.sql"
            correction_bytes=correction.read_bytes()
            report["source_files"][correction.as_posix().removeprefix(ROOT.as_posix()+"/")]=hashlib.sha256(correction_bytes).hexdigest()
            correction_sql=re.sub(r"(?m)^--.*$","",correction_bytes.decode("utf8"))
            corrections=[x.strip() for x in correction_sql.split(";") if x.strip()]
            if len(corrections)!=4 or not all(x.startswith("ALTER TABLE `member_") for x in corrections):
                raise AssertionError("Unexpected tenant interceptor SQL")
            for statement in corrections:
                cur.execute(statement)
            print("TENANT_COMPATIBILITY_ALIGNED",len(corrections),flush=True)
            after=table_names(cur)
            report["tables_after_all_migrations"]=len(after)
            checks["67_unique_module_tables"]=len(names_expected)==67 and after-base==names_expected
            checks["all_original_tables_preserved"]=base.issubset(after) and len(after)==len(base)+67
            checks["151_to_188_additive_only"]=bool(staged_151) and staged_151.issubset(after) and (after-staged_151)==names_expected-staged_151 and len(after-staged_151)==37
            checks["151_seed_rows_preserved_at_188"]=stage_151_seed_rows=={
                name:table_rows(cur,name) for name in CHECK_EXISTING}
            cur.execute("SELECT table_type, COUNT(*) FROM information_schema.tables WHERE table_schema=%s GROUP BY table_type",(db,))
            final_types=dict(cur.fetchall())
            checks["188_objects_187_tables_one_view"]=final_types.get("BASE TABLE")==187 and final_types.get("VIEW")==1
            cur.execute("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=%s AND column_name='tenant_id' AND table_name IN ("+
                        ",".join(["'"+n+"'" for n in sorted(names_expected)])+")",(db,))
            checks["all_67_new_tables_tenant_scoped_after_v06"]=cur.fetchone()[0]==67
            checks["baseline_rows_unchanged"]=all(table_rows(cur,t)==v for t,v in immutable_counts.items())
            for table in ("member_user","product_spu","trade_order","promotion_coupon","pay_order","pay_refund","pay_wallet","pay_wallet_transaction","pay_transfer"):
                checks["empty_"+table]=table_rows(cur,table)==0
            # No payment application/channel is seeded by migrations or enabled by default.
            for table in ("pay_app","pay_channel"):
                checks["empty_"+table]=table_rows(cur,table)==0
                cur.execute("SELECT column_default FROM information_schema.columns WHERE table_schema=%s AND table_name=%s AND column_name='status'",(db,table))
                default=cur.fetchone()
                checks["default_disabled_"+table]=default is not None and str(default[0])=="1"
            # Signpost the known bootstrap account security issue without printing credentials.
            report["seed_admin_account_count"]=immutable_counts["system_users"]
            report["requires_default_admin_remediation"]=immutable_counts["system_users"]>0
            checks["no_real_finance_rows_introduced"]=(
                table_rows(cur,"cps_rebate_asset_ledger")==immutable_counts["cps_rebate_asset_ledger"]
                and table_rows(cur,"cps_withdraw")==immutable_counts["cps_withdraw"])
    finally:
        if created:
            with conn.cursor() as cur:
                cur.execute("DROP DATABASE IF EXISTS "+BT+db+BT)
            print("FULL_BASELINE_DISPOSABLE_DATABASE_REMOVED=YES",flush=True)
        conn.close()
    report["passed"]=bool(checks) and all(checks.values())
    output=ROOT/"full-baseline-migration-ci-report.json"
    output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print("FULL_BASELINE_CHECKS",json.dumps(checks,sort_keys=True),flush=True)
    print("TABLES",report.get("baseline_table_count"),report.get("tables_after_all_migrations"),flush=True)
    if not report["passed"]:
        raise SystemExit("Repository baseline + migration compatibility failed")
    print("FULL_BASELINE_AND_67_MODULE_TABLES_PASS",flush=True)

if __name__=="__main__":
    main()
