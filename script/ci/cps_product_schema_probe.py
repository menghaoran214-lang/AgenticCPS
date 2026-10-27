#!/usr/bin/env python3
"""Verify product MySQL 8 migration in a disposable Actions database. Not a production release test."""
import json
import os
from pathlib import Path
import re
import secrets
import time

import pymysql

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "backend/sql/migrations/V20261028_02__product_core_mysql8.sql"
TABLES = {
    "product_brand", "product_category", "product_property", "product_property_value",
    "product_spu", "product_sku", "product_favorite", "product_browse_history",
    "product_comment",
}
JSON_FIELDS = {"product_spu": {"slider_pic_urls"}, "product_sku": {"properties"},
               "product_comment": {"sku_properties", "pic_urls"}}
REQUIRED = {
    "product_brand": {"id", "name", "pic_url", "sort", "description", "status"},
    "product_category": {"id", "parent_id", "name", "pic_url", "sort", "status"},
    "product_property": {"id", "name", "remark"},
    "product_property_value": {"id", "property_id", "name", "remark"},
    "product_spu": {"id", "name", "keyword", "introduction", "description",
                    "category_id", "brand_id", "pic_url", "slider_pic_urls",
                    "sort", "status", "spec_type", "price", "market_price", "cost_price",
                    "stock", "delivery_types", "delivery_template_id", "give_integral",
                    "sub_commission_type", "sales_count", "virtual_sales_count",
                    "browse_count"},
    "product_sku": {"id", "spu_id", "properties", "price", "market_price", "cost_price",
                    "bar_code", "pic_url", "stock", "weight", "volume",
                    "first_brokerage_price", "second_brokerage_price", "sales_count"},
    "product_favorite": {"id", "user_id", "spu_id"},
    "product_browse_history": {"id", "spu_id", "user_id", "user_deleted"},
    "product_comment": {"id", "user_id", "user_nickname", "user_avatar", "anonymous",
                        "order_id", "order_item_id", "spu_id", "spu_name", "sku_id",
                        "sku_pic_url", "sku_properties", "visible", "scores",
                        "description_scores", "benefit_scores", "content", "pic_urls",
                        "reply_status", "reply_user_id", "reply_content", "reply_time"},
}
AUDIT = {"creator", "create_time", "updater", "update_time", "deleted", "tenant_id"}
def main():
    if os.getenv("GITHUB_ACTIONS") != "true" or os.getenv("CPS_PRODUCT_SCHEMA_ISOLATED") != "1":
        raise RuntimeError("Refusing to modify any non-CI database")
    if os.getenv("MYSQL_HOST") not in ("localhost", "127.0.0.1"):
        raise RuntimeError("Only GitHub Actions local MySQL accepted")
    db="cps_product_schema_ci_"+secrets.token_hex(5)
    conn=None
    for attempt in range(30):
        try:
            conn=pymysql.connect(host="127.0.0.1",port=3306,user="root",
                password=os.environ["MYSQL_ROOT_PASSWORD"],charset="utf8mb4",
                autocommit=True,connect_timeout=3)
            break
        except pymysql.err.OperationalError:
            if attempt==29:raise
            time.sleep(2)
    checks={}
    try:
        with conn.cursor() as cur:
            cur.execute("CREATE DATABASE `"+db+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
            cur.execute("USE `"+db+"`")
            raw=re.sub(r"(?m)^--.*$", "", SOURCE.read_text(encoding="utf8"))
            statements=[s.strip() for s in raw.split(";") if s.strip()]
            checks["nine_create_statements"]=len(statements)==9 and all(
                s.startswith("CREATE TABLE `product_") for s in statements)
            if not checks["nine_create_statements"]:raise AssertionError("Unexpected migration statements")
            for statement in statements:cur.execute(statement)
            cur.execute("SHOW TABLES")
            checks["nine_expected_tables"]={row[0] for row in cur.fetchall()}==TABLES
            model_ok=True
            json_ok=True
            audit_ok=True
            indexed=True
            for table in sorted(TABLES):
                cur.execute("SHOW COLUMNS FROM `"+table+"`")
                cols={row[0]:row[1].lower() for row in cur.fetchall()}
                model_ok &= REQUIRED[table].issubset(cols)
                audit_ok &= AUDIT.issubset(cols)
                for field in JSON_FIELDS.get(table,set()):
                    json_ok &= cols.get(field)=="json"
                cur.execute("SHOW INDEX FROM `"+table+"`")
                indices=cur.fetchall()
                indexed &= any(row[2]=="PRIMARY" for row in indices) and any(row[2]!="PRIMARY" for row in indices)
            checks["model_fields_match"]=bool(model_ok)
            checks["all_tables_audited_and_tenant_scoped"]=bool(audit_ok)
            checks["json_fields_native_mysql_type"]=bool(json_ok)
            checks["primary_and_query_indexes"]=bool(indexed)
            cur.execute("INSERT INTO product_brand (name,tenant_id) VALUES ('ci-brand',1)")
            brand=cur.lastrowid
            cur.execute("INSERT INTO product_category (parent_id,name,tenant_id) VALUES (0,'ci-category',1)")
            category=cur.lastrowid
            cur.execute("INSERT INTO product_spu (name,category_id,brand_id,slider_pic_urls,tenant_id) VALUES "
                        "(%s,%s,%s,%s,1)",("ci-product",category,brand,'["https://example.invalid/test.jpg"]'))
            spu=cur.lastrowid
            cur.execute("INSERT INTO product_sku (spu_id,properties,price,stock,tenant_id) VALUES "
                        "(%s,%s,100,1,1)",(spu,'[{"propertyId":1,"valueId":2}]'))
            cur.execute("SELECT COUNT(*) FROM product_sku AS s JOIN product_spu AS p ON p.id=s.spu_id "
                        "WHERE p.id=%s AND p.tenant_id=1 AND s.tenant_id=1",(spu,))
            checks["sample_spu_sku_join"]=cur.fetchone()[0]==1
            cur.execute("SELECT JSON_VALID(slider_pic_urls) FROM product_spu WHERE id=%s",(spu,))
            checks["json_roundtrip"]=cur.fetchone()[0]==1
    finally:
        with conn.cursor() as cur:cur.execute("DROP DATABASE IF EXISTS `"+db+"`")
        conn.close()
        print("ISOLATED_PRODUCT_DATABASE_DROPPED",flush=True)
    report={"scope":"9 product Java entity tables and isolated MySQL syntax/constraints only",
            "checks":checks,"passed":all(checks.values()),"production_ready":False}
    (ROOT/"product-schema-ci-report.json").write_text(json.dumps(report,indent=2)+"\n")
    print("PRODUCT_SCHEMA_CHECKS",json.dumps(checks,sort_keys=True))
    if not report["passed"]:raise SystemExit("Product schema checks failed")

if __name__=="__main__":
    main()
