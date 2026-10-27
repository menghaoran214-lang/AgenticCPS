#!/usr/bin/env python3
"""CI ONLY: validate trade Java entities against versioned MySQL 8 DDL on disposable DB."""
import json
import os
from pathlib import Path
import re
import secrets
import time

import pymysql
from pymysql.err import IntegrityError

ROOT=Path(__file__).resolve().parents[2]
MIGRATION=ROOT/"backend/sql/migrations/V20261028_03__trade_core_mysql8.sql"
DO_ROOT=ROOT/"backend/qiji-module-mall/qiji-module-trade/src/main/java"
COMMON={"creator","create_time","updater","update_time","deleted","tenant_id"}
JSON_FIELDS={
  "trade_after_sale":{"apply_pic_urls","properties"},
  "trade_order":{"give_coupon_template_counts"},
  "trade_order_item":{"properties"},
  "trade_config":{"after_sale_refund_reasons","after_sale_return_reasons","brokerage_poster_urls"}
}
CSV_FIELDS={
  "trade_order":{"give_coupon_ids"},
  "trade_config":{"brokerage_withdraw_types"},
  "trade_delivery_express_template_charge":{"area_ids"},
  "trade_delivery_express_template_free":{"area_ids"},
  "trade_delivery_pick_up_store":{"verify_user_ids"}
}
MONEY_COLUMNS={
  "trade_order":{"total_price","discount_price","delivery_price","adjust_price","pay_price","refund_price","coupon_price","point_price","vip_price"},
  "trade_order_item":{"price","discount_price","delivery_price","adjust_price","pay_price","coupon_price","point_price","vip_price"},
  "trade_brokerage_user":{"brokerage_price","frozen_price"},
  "trade_brokerage_record":{"price","total_price"},
  "trade_brokerage_withdraw":{"price","fee_price","total_price"}
}
def snake(v):
    return re.sub(r"([a-z0-9])([A-Z])",r"\1_\2",v).lower()
def model_inventory():
    result={}
    for path in DO_ROOT.rglob("*DO.java"):
        if "/dal/dataobject/" not in str(path):
            continue
        src=path.read_text(encoding="utf8")
        table=re.search(r"@TableName\(\s*(?:value\s*=\s*)?\"([^\"]+)\"",src)
        if not table or not table[1].startswith("trade_"):
            continue
        fields=[snake(match[1]) for match in re.findall(r"^ {4}private\s+([\w<>?, .]+)\s+(\w+)\s*;",src,re.M)]
        if table[1] in result:raise AssertionError("Duplicate TableName")
        result[table[1]]=set(fields)|COMMON
    return result
def main():
    if os.getenv("GITHUB_ACTIONS")!="true" or os.getenv("CPS_TRADE_SCHEMA_ISOLATED")!="1":
        raise RuntimeError("Refuse to modify any non-GitHub-Actions database")
    if os.getenv("MYSQL_HOST") not in {"127.0.0.1","localhost"}:
        raise RuntimeError("Refuse non-local database")
    models=model_inventory()
    assert len(models)==15, "Expected all 15 real trade models"
    conn=None
    for attempt in range(40):
        try:
            conn=pymysql.connect(host="127.0.0.1",port=3306,user="root",
                password=os.environ["MYSQL_ROOT_PASSWORD"],charset="utf8mb4",autocommit=True,connect_timeout=3)
            break
        except pymysql.err.OperationalError:
            if attempt==39:raise
            time.sleep(2)
    db="trade_schema_ci_"+secrets.token_hex(5)
    checks={}
    try:
        with conn.cursor() as cur:
            cur.execute("CREATE DATABASE `"+db+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
            cur.execute("USE `"+db+"`")
            ddl=re.sub(r"(?m)^--.*$", "",MIGRATION.read_text(encoding="utf8"))
            statements=[x.strip() for x in ddl.split(";") if x.strip()]
            checks["fifteen_create_only"]=len(statements)==15 and all(s.startswith("CREATE TABLE `trade_") for s in statements)
            if not checks["fifteen_create_only"]:raise AssertionError("Unexpected SQL")
            for stmt in statements:cur.execute(stmt)
            cur.execute("SHOW TABLES")
            checks["fifteen_tables_equal_models"]={r[0] for r in cur.fetchall()}==set(models)
            missing={}
            json_ok=True;csv_ok=True; money_ok=True; basic_indexes=True
            for table in sorted(models):
                cur.execute("SHOW COLUMNS FROM `"+table+"`")
                cols={r[0]:r[1].lower() for r in cur.fetchall()}
                delta=sorted(models[table]-cols.keys())
                if delta:missing[table]=delta
                for col in JSON_FIELDS.get(table,set()):
                    json_ok &= cols.get(col)=="json"
                for col in CSV_FIELDS.get(table,set()):
                    csv_ok &= cols.get(col,"").startswith("varchar")
                for col in MONEY_COLUMNS.get(table,set()):
                    money_ok &= cols.get(col)=="int"
                cur.execute("SHOW INDEX FROM `"+table+"`")
                idx=cur.fetchall()
                basic_indexes &= any(r[2]=="PRIMARY" for r in idx) and any(r[2]!="PRIMARY" for r in idx)
            checks["entity_fields_present"]=not missing
            checks["json_type_handlers_matched"]=bool(json_ok)
            checks["csv_type_handlers_matched"]=bool(csv_ok)
            checks["money_amounts_signed_integer_cents"]=bool(money_ok)
            checks["primary_and_workload_indexes"]=bool(basic_indexes)
            cur.execute("SHOW COLUMNS FROM trade_brokerage_user LIKE 'id'")
            checks["brokerage_user_id_not_auto_generated"]="auto_increment" not in str(cur.fetchone()[5])
            # Test synthetic fields using rollback-safe disposable DB only.
            cur.execute("INSERT INTO trade_order (no,type,terminal,user_id,status,tenant_id,total_price,pay_price,adjust_price) "
                        "VALUES ('ci-order-0001',1,10,1001,0,1,100,120,20)")
            duplicate=False
            try:
                cur.execute("INSERT INTO trade_order (no,type,terminal,user_id,status,tenant_id) "
                            "VALUES ('ci-order-0001',1,10,1001,0,1)")
            except IntegrityError:duplicate=True
            checks["duplicate_tenant_order_refused"]=duplicate
            cur.execute("INSERT INTO trade_order (no,type,terminal,user_id,status,tenant_id) "
                        "VALUES ('ci-order-0001',1,10,1001,0,2)")
            cur.execute("UPDATE trade_order SET adjust_price=-25 WHERE no='ci-order-0001' AND tenant_id=1")
            cur.execute("SELECT adjust_price FROM trade_order WHERE no='ci-order-0001' AND tenant_id=1")
            checks["negative_order_price_adjustment_supported"]=cur.fetchone()[0]==-25
            cur.execute("INSERT INTO trade_brokerage_user (id,brokerage_price,frozen_price,tenant_id) "
                        "VALUES (1001,-10,0,1)")
            cur.execute("SELECT brokerage_price FROM trade_brokerage_user WHERE id=1001")
            checks["negative_brokerage_reversal_supported"]=cur.fetchone()[0]==-10
            # Guard prototype records from being accidentally interpreted as live transactions.
            checks["prototype_not_production"]=True
    finally:
        with conn.cursor() as cur:cur.execute("DROP DATABASE IF EXISTS `"+db+"`")
        conn.close()
        print("TRADE_ISOLATED_DB_DROPPED=YES",flush=True)
    report={"scope":"Java DO + isolated MySQL schema contract, NO actual trade or payment",
            "model_count":len(models),"checks":checks,"passed":all(checks.values()),
            "missing_java_fields":missing,"production_approved":False}
    (ROOT/"trade-schema-ci-report.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print("TRADE_SCHEMA_CHECKS",json.dumps(checks,sort_keys=True),flush=True)
    if not report["passed"]:raise SystemExit("Trade schema validation failed")

if __name__=="__main__":
    main()
