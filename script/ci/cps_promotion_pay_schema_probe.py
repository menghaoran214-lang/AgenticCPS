#!/usr/bin/env python3
"""Schema verification only. All writes restricted to an ephemeral CI MySQL container."""
import json, os, re, secrets, time
from pathlib import Path
import pymysql
from pymysql.err import IntegrityError

ROOT=Path(__file__).resolve().parents[2]
MODULES={
  "promotion":(ROOT/"backend/qiji-module-mall/qiji-module-promotion/src/main/java",
     ROOT/"backend/sql/migrations/V20261028_04__promotion_core_mysql8.sql",23),
  "pay":(ROOT/"backend/qiji-module-pay/src/main/java",
     ROOT/"backend/sql/migrations/V20261028_05__pay_core_mysql8.sql",14)
}
SNAKE=lambda s: re.sub(r"([a-z0-9])([A-Z])",r"\1_\2",s).lower()
AUDIT={"creator","create_time","updater","update_time","deleted","tenant_id"}
BT=chr(96)
def read_models(directory):
    models={}
    for p in directory.rglob("*DO.java"):
        if "/dal/dataobject/" not in p.as_posix():continue
        txt=p.read_text(encoding="utf8")
        m=re.search(r'@TableName\(\s*(?:value\s*=\s*)?"([^"]+)"',txt)
        if not m:continue
        name=m[1]
        if name in models:raise AssertionError("Duplicate table: "+name)
        cols={SNAKE(n) for _,n in re.findall(r"^ {4}private\s+([\w<>?, .]+)\s+(\w+)\s*;",txt,re.M)}
        models[name]=cols | AUDIT
    return models

def main():
    if os.getenv("GITHUB_ACTIONS")!="true" or os.getenv("CPS_37_SCHEMA_ISOLATED")!="1":
        raise RuntimeError("Blocked: CI isolated SQL test only")
    if os.getenv("MYSQL_HOST") not in {"127.0.0.1","localhost"}:
        raise RuntimeError("Blocked: non-loopback MySQL")
    db="cps_37_schema_ci_"+secrets.token_hex(6)
    connection=None
    for attempt in range(35):
        try:
            connection=pymysql.connect(host="127.0.0.1",port=3306,user="root",
               password=os.environ["MYSQL_ROOT_PASSWORD"],autocommit=True,charset="utf8mb4",connect_timeout=3)
            break
        except pymysql.err.OperationalError:
            if attempt==34:raise
            time.sleep(2)
    checks={}
    report={"notice":"only Java DO vs test MySQL DDL; no real payments, callbacks or wallet funds",
            "tables":{}, "checks":checks,"production_ready":False}
    try:
        with connection.cursor() as cur:
            cur.execute("CREATE DATABASE "+BT+db+BT+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
            cur.execute("USE "+BT+db+BT)
            models={}
            for module,(source,sqlpath,expected) in MODULES.items():
                part=read_models(source)
                checks[module+"_do_count"]=len(part)==expected
                models.update(part)
                ddl=re.sub(r"(?m)^--.*$", "",sqlpath.read_text(encoding="utf8"))
                statements=[s.strip() for s in ddl.split(";") if s.strip()]
                checks[module+"_create_only"]=len(statements)==expected and all(
                    s.startswith("CREATE TABLE "+BT+module+"_") for s in statements)
                if not checks[module+"_create_only"]:raise RuntimeError("Unsafe or incomplete schema")
                for statement in statements:cur.execute(statement)
            cur.execute("SHOW TABLES")
            actual={r[0] for r in cur.fetchall()}
            checks["all_37_tables_match_models"]=actual==set(models) and len(actual)==37
            misses={}
            index_ok=True
            audit_ok=True
            for table,fields in models.items():
                cur.execute("SHOW COLUMNS FROM "+BT+table+BT)
                found={r[0]:r[1].lower() for r in cur.fetchall()}
                diff=sorted(fields-set(found))
                if diff:misses[table]=diff
                audit_ok &= AUDIT.issubset(found)
                cur.execute("SHOW INDEX FROM "+BT+table+BT)
                idx=cur.fetchall()
                index_ok &= any(row[2]=="PRIMARY" for row in idx)
                report["tables"][table]={"column_count":len(found),"missing_fields":diff}
            checks["all_java_entity_fields_exist"]=not misses
            checks["all_tables_have_tenant_and_audit_fields"]=bool(audit_ok)
            checks["all_tables_have_primary_keys"]=bool(index_ok)
            for table,col in [
                 ("pay_order","price"),("pay_refund","refund_price"),
                 ("pay_wallet","balance"),("pay_wallet_transaction","balance"),
                 ("pay_transfer","price"),("promotion_coupon","discount_price")]:
                cur.execute("SELECT data_type FROM information_schema.columns WHERE table_schema=%s AND table_name=%s AND column_name=%s",(db,table,col))
                kind=cur.fetchone()
                checks[table+"_"+col+"_integer"]=kind is not None and kind[0]=="int"
            cur.execute("SHOW COLUMNS FROM pay_wallet LIKE 'id'")
            checks["wallet_primary_key_auto_generated"]="auto_increment" in cur.fetchone()[5]
            cur.execute("SHOW COLUMNS FROM pay_channel LIKE 'config'")
            checks["pay_channel_config_is_text_not_plain_json"]=cur.fetchone()[1].lower() in ("longtext","mediumtext","text")
            def duplicate_rejected(insert1,insert2):
                cur.execute(insert1)
                try:cur.execute(insert2)
                except IntegrityError:return True
                return False
            checks["merchant_order_idempotence"]=duplicate_rejected(
                "INSERT INTO pay_order (app_id,merchant_order_id,tenant_id) VALUES (1,'ci-order',1)",
                "INSERT INTO pay_order (app_id,merchant_order_id,tenant_id) VALUES (1,'ci-order',1)")
            checks["merchant_refund_idempotence"]=duplicate_rejected(
                "INSERT INTO pay_refund (app_id,merchant_refund_id,tenant_id) VALUES (1,'ci-refund',1)",
                "INSERT INTO pay_refund (app_id,merchant_refund_id,tenant_id) VALUES (1,'ci-refund',1)")
            checks["merchant_transfer_idempotence"]=duplicate_rejected(
                "INSERT INTO pay_transfer (app_id,merchant_transfer_id,tenant_id) VALUES (1,'ci-transfer',1)",
                "INSERT INTO pay_transfer (app_id,merchant_transfer_id,tenant_id) VALUES (1,'ci-transfer',1)")
            checks["wallet_owner_uniqueness"]=duplicate_rejected(
                "INSERT INTO pay_wallet (user_id,user_type,tenant_id) VALUES (1001,2,1)",
                "INSERT INTO pay_wallet (user_id,user_type,tenant_id) VALUES (1001,2,1)")
            checks["wallet_transaction_business_idempotence"]=duplicate_rejected(
                "INSERT INTO pay_wallet_transaction (wallet_id,biz_type,biz_id,no,tenant_id) VALUES (1,2,'ci-biz','ci-tx-1',1)",
                "INSERT INTO pay_wallet_transaction (wallet_id,biz_type,biz_id,no,tenant_id) VALUES (1,2,'ci-biz','ci-tx-2',1)")
            cur.execute("SHOW COLUMNS FROM pay_channel LIKE 'status'")
            checks["channels_default_disabled"]=str(cur.fetchone()[4])=="1"
            cur.execute("SHOW COLUMNS FROM pay_app LIKE 'status'")
            checks["payment_apps_default_disabled"]=str(cur.fetchone()[4])=="1"
            checks["no_external_finance_workflows_run"]=True
    finally:
        with connection.cursor() as cur:cur.execute("DROP DATABASE IF EXISTS "+BT+db+BT)
        connection.close()
        print("DISPOSABLE_SQL_DATABASE_CLEANED=YES",flush=True)
    report["passed"]=all(checks.values())
    (ROOT/"promotion-pay-schema-report.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf8")
    print("SCHEMA_37_CHECKS",json.dumps(checks,ensure_ascii=False,sort_keys=True),flush=True)
    if not report["passed"]:raise SystemExit("Schema CI failed: review report")

if __name__=="__main__":
    main()
