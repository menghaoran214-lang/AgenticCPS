#!/usr/bin/env python3
"""CI-only integration of AgenticCPS five versioned MySQL 8 baseline modules.

Never contacts the cloud VPS and never touches any non-loopback MySQL server.
Success proves additive schema composition, not real ordering/payment correctness.
"""
import json
import os
from pathlib import Path
import re
import secrets
import time

import pymysql
from pymysql.err import IntegrityError

ROOT=Path(__file__).resolve().parents[2]
MIGRATIONS=[
    ("member", "V20261028_01__member_core_mysql8.sql", 6),
    ("product", "V20261028_02__product_core_mysql8.sql", 9),
    ("trade", "V20261028_03__trade_core_mysql8.sql", 15),
    ("promotion", "V20261028_04__promotion_core_mysql8.sql", 23),
    ("pay", "V20261028_05__pay_core_mysql8.sql", 14),
]
BASE_FIELDS=("creator","create_time","updater","update_time","deleted","tenant_id")
BT=chr(96)

def open_local():
    for i in range(45):
        try:
            return pymysql.connect(host="127.0.0.1",port=3306,user="root",
                password=os.environ["MYSQL_ROOT_PASSWORD"],autocommit=True,charset="utf8mb4",connect_timeout=3)
        except pymysql.err.OperationalError:
            if i == 44:raise
            time.sleep(2)

def migration_sql(path, prefix, expected):
    text=re.sub(r"(?m)^--.*$", "",path.read_text(encoding="utf8"))
    statements=[s.strip() for s in text.split(";") if s.strip()]
    assert len(statements)==expected, (path.name,len(statements))
    assert all(s.startswith("CREATE TABLE "+BT+prefix+"_") for s in statements),path.name
    return statements

def main():
    if os.environ.get("GITHUB_ACTIONS")!="true" or os.environ.get("CPS_67_SCHEMA_ISOLATED")!="1":
        raise RuntimeError("This script is restricted to isolated GitHub Actions")
    if os.environ.get("MYSQL_HOST") not in ("localhost","127.0.0.1"):
        raise RuntimeError("Refusing remote DB access")
    enum_path=ROOT/"backend/qiji-framework/qiji-common/src/main/java/com/qiji/cps/framework/common/enums/CommonStatusEnum.java"
    java_enum=enum_path.read_text(encoding="utf8")
    assert re.search(r"\bENABLE\(0,",java_enum)
    assert re.search(r"\bDISABLE\(1,",java_enum)
    db="cps_schema_all_ci_"+secrets.token_hex(7)
    connection=open_local()
    checks={}
    report={"purpose":"5-migration additive schema integration; no live payments, no real funding",
            "table_counts":{},"checks":checks,"release_approved":False}
    try:
        with connection.cursor() as cur:
            cur.execute("CREATE DATABASE "+BT+db+BT+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
            cur.execute("USE "+BT+db+BT)
            for module,file,count in MIGRATIONS:
                path=ROOT/"backend/sql/migrations"/file
                statements=migration_sql(path,module,count)
                for statement in statements:cur.execute(statement)
                report["table_counts"][module]=count
                print("APPLIED_TO_TEMP_DB",module,count,flush=True)
            cur.execute("SHOW TABLES")
            tables={r[0] for r in cur.fetchall()}
            checks["67_unique_expected_tables"]=len(tables)==67 and all(
                sum(name.startswith(module+"_") for name in tables)==count
                for module,_,count in MIGRATIONS)
            cur.execute("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=%s AND column_name='tenant_id'",(db,))
            checks["67_tenant_columns"]=cur.fetchone()[0]==67
            for field in BASE_FIELDS:
                cur.execute("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=%s AND column_name=%s",(db,field))
                checks["67_"+field+"_columns"]=cur.fetchone()[0]==67
            for table,col in (("pay_order","price"),("pay_refund","refund_price"),
                              ("pay_wallet","balance"),("trade_order","pay_price"),
                              ("promotion_coupon","discount_price")):
                cur.execute("SELECT data_type FROM information_schema.columns WHERE table_schema=%s AND table_name=%s AND column_name=%s",(db,table,col))
                row=cur.fetchone()
                checks[table+"_"+col+"_int"]=bool(row) and row[0]=="int"
            for table in ("pay_app","pay_channel"):
                cur.execute("SELECT column_default FROM information_schema.columns WHERE table_schema=%s AND table_name=%s AND column_name='status'",(db,table))
                row=cur.fetchone()
                checks[table+"_disabled_by_default"]=bool(row) and str(row[0])=="1"
            # Representative multi-module foreign-ID lookups on isolated synthetic rows.
            cur.execute("INSERT INTO member_user (mobile,password,tenant_id,status) VALUES ('19900000012','ci-hash',1,0)")
            member=cur.lastrowid
            cur.execute("INSERT INTO product_brand (name,tenant_id) VALUES ('ci-brand',1)")
            brand=cur.lastrowid
            cur.execute("INSERT INTO product_category (name,parent_id,tenant_id) VALUES ('ci-category',0,1)")
            category=cur.lastrowid
            cur.execute("INSERT INTO product_spu (name,category_id,brand_id,tenant_id) VALUES (%s,%s,%s,1)",("ci-product",category,brand))
            product=cur.lastrowid
            cur.execute("INSERT INTO product_sku (spu_id,price,stock,tenant_id) VALUES (%s,200,3,1)",(product,))
            sku=cur.lastrowid
            cur.execute("INSERT INTO trade_order (no,type,terminal,user_id,status,tenant_id) VALUES ('ci-trade-1',1,10,%s,0,1)",(member,))
            order=cur.lastrowid
            cur.execute("SELECT COUNT(*) FROM trade_order o JOIN member_user m ON m.id=o.user_id AND m.tenant_id=o.tenant_id WHERE o.id=%s",(order,))
            checks["member_trade_cross_module_query"]=cur.fetchone()[0]==1
            cur.execute("SELECT COUNT(*) FROM product_sku sk JOIN product_spu sp ON sk.spu_id=sp.id AND sk.tenant_id=sp.tenant_id WHERE sk.id=%s",(sku,))
            checks["spu_sku_cross_module_query"]=cur.fetchone()[0]==1
            # Test candidate index constraints only.  This does not simulate payment.
            cur.execute("INSERT INTO pay_wallet (user_id,user_type,tenant_id) VALUES (%s,2,1)",(member,))
            duplicate=False
            try:
                cur.execute("INSERT INTO pay_wallet (user_id,user_type,tenant_id) VALUES (%s,2,1)",(member,))
            except IntegrityError:duplicate=True
            checks["one_wallet_per_user_type_tenant"]=duplicate
            cur.execute("SELECT COUNT(*) FROM pay_order")
            checks["no_payment_executed"]=cur.fetchone()[0]==0
            cur.execute("SELECT COUNT(*) FROM pay_transfer")
            checks["no_transfer_executed"]=cur.fetchone()[0]==0
    finally:
        with connection.cursor() as cur:cur.execute("DROP DATABASE IF EXISTS "+BT+db+BT)
        connection.close()
        print("ALL_MODULES_TEMP_DATABASE_DROPPED=YES",flush=True)
    report["passed"]=bool(checks) and all(checks.values())
    (ROOT/"all-module-mysql8-ci-report.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n",encoding="utf8")
    print("ALL_MODULE_SCHEMA_CHECKS",json.dumps(checks,sort_keys=True),flush=True)
    if not report["passed"]:raise SystemExit("Composed database verification failed")
    print("FIVE_MODULE_MIGRATIONS_67_TABLES=PASS")

if __name__=="__main__":
    main()
