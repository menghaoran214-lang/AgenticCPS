"""Disposable station journey. No fixture or mutation endpoints are added to the server."""
import argparse
import hmac
import json
import os
import secrets
import shutil
import time
from decimal import Decimal
from pathlib import Path
from urllib.parse import urlsplit
from urllib.request import Request, urlopen
from urllib.error import HTTPError

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / '.station-ci'
BASE = 'http://127.0.0.1:18081'
DATABASE = 'cps_station_ci'
AGENT, BUYER, OTHER, DISABLED, WEB_BUYER = 900001, 900002, 900003, 900004, 900005
PHONES = {AGENT: '13800001001', BUYER: '13800001002', OTHER: '13800001003',
          DISABLED: '13800001004', WEB_BUYER: '13800001005'}


def require_isolated(base, enabled):
    url = urlsplit(base)
    if enabled != '1' or url.scheme != 'http' or url.hostname != '127.0.0.1' or url.port != 18081 \
            or url.username or url.password or url.path or url.query or url.fragment:
        raise ValueError('Only the explicit, loopback-only station test environment is accepted')


def marker_matches(expected, actual):
    return bool(expected and actual and hmac.compare_digest(expected, actual))


def connect():
    require_isolated(BASE, os.environ.get('CPS_STATION_ISOLATED'))
    import pymysql
    connection = pymysql.connect(host='127.0.0.1', port=13307, user='qiji',
                                 password=os.environ['MYSQL_PASSWORD'], database=DATABASE,
                                 charset='utf8mb4', autocommit=True,
                                 cursorclass=pymysql.cursors.DictCursor)
    with connection.cursor() as cursor:
        cursor.execute('SELECT nonce FROM ci_station_marker WHERE id=1')
        marker = cursor.fetchone()
    if not marker_matches(os.environ.get('CPS_STATION_NONCE'), marker and marker['nonce']):
        connection.close()
        raise ValueError('Database marker mismatch; refusing all fixture writes')
    return connection


def init_workspace():
    require_isolated(BASE, os.environ.get('CPS_STATION_ISOLATED'))
    env_path = WORK / 'runtime.env'
    if env_path.exists():
        raise ValueError('Test credentials already exist; reuse or explicitly stop/reset this test environment')
    WORK.mkdir(exist_ok=True)
    for name in ['mysql-init', 'server', 'admin', 'h5', 'evidence']:
        (WORK / name).mkdir(exist_ok=True)
    values = {
        'MYSQL_ROOT_PASSWORD': secrets.token_hex(20),
        'MYSQL_PASSWORD': secrets.token_hex(20),
        'REDIS_PASSWORD': secrets.token_hex(20),
        'CPS_STATION_NONCE': secrets.token_hex(24),
        'CPS_STATION_PASSWORD': 'A1' + secrets.token_hex(5),
        'CPS_STATION_ISOLATED': '1',
    }
    env_path.write_text(''.join(f'{key}={value}\n' for key, value in values.items()), encoding='utf-8')
    env_path.chmod(0o600)
    for source, target in [
        ('backend/sql/mysql/ruoyi-vue-pro.sql', '00-system.sql'),
        ('backend/sql/mysql/quartz.sql', '05-quartz.sql'),
        ('backend/sql/module/cps-all-in-one.sql', '10-cps.sql'),
    ]:
        shutil.copyfile(ROOT / source, WORK / 'mysql-init' / target)
    nonce = values['CPS_STATION_NONCE']
    (WORK / 'mysql-init/99-isolated-marker.sql').write_text(
        'CREATE TABLE ci_station_marker(id INT PRIMARY KEY, nonce VARCHAR(64) NOT NULL);\n'
        f"INSERT INTO ci_station_marker VALUES (1, '{nonce}');\n"
        'DELETE FROM infra_job;\n', encoding='utf-8')
    print('Created disposable test workspace. Credentials are not printed.')


def query(connection, sql, parameters=()):
    with connection.cursor() as cursor:
        cursor.execute(sql, parameters)
        return cursor.fetchall() if cursor.description else cursor.rowcount


def prepare():
    connection = connect()
    import bcrypt
    password_hash = bcrypt.hashpw(os.environ['CPS_STATION_PASSWORD'].encode(), bcrypt.gensalt()).decode()
    # CI fixture tables cover missing upstream member/decorate schemas only.
    # These are deliberately outside production initialization SQL.
    query(connection, """
        CREATE TABLE member_user (
            id BIGINT PRIMARY KEY AUTO_INCREMENT, mobile VARCHAR(11) NOT NULL, password VARCHAR(100) NOT NULL,
            status TINYINT NOT NULL DEFAULT 0, register_ip VARCHAR(32) DEFAULT '127.0.0.1', register_terminal INT,
            login_ip VARCHAR(50) DEFAULT '', login_date DATETIME, nickname VARCHAR(64) DEFAULT '',
            avatar VARCHAR(512) DEFAULT '', name VARCHAR(64), sex TINYINT, birthday DATETIME,
            area_id INT, mark VARCHAR(255), point INT DEFAULT 0, tag_ids VARCHAR(255),
            level_id BIGINT, experience INT DEFAULT 0, group_id BIGINT,
            creator VARCHAR(64) DEFAULT '', updater VARCHAR(64) DEFAULT '',
            create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            deleted BIT DEFAULT 0, tenant_id BIGINT NOT NULL DEFAULT 1,
            UNIQUE KEY ci_member_mobile(tenant_id,mobile))
        """)
    query(connection, """
        CREATE TABLE member_level (
            id BIGINT PRIMARY KEY, name VARCHAR(64), level INT, experience INT,
            discount_percent INT, icon VARCHAR(512), background_url VARCHAR(512), status TINYINT DEFAULT 0,
            creator VARCHAR(64) DEFAULT '', updater VARCHAR(64) DEFAULT '',
            create_time DATETIME DEFAULT CURRENT_TIMESTAMP, update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            deleted BIT DEFAULT 0, tenant_id BIGINT NOT NULL DEFAULT 1)
        """)
    common = """creator VARCHAR(64) DEFAULT '', updater VARCHAR(64) DEFAULT '',
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP, update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                deleted BIT DEFAULT 0, tenant_id BIGINT NOT NULL DEFAULT 1"""
    query(connection, f"""CREATE TABLE promotion_diy_template (
            id BIGINT PRIMARY KEY, name VARCHAR(64), used BIT DEFAULT 0, used_time DATETIME,
            remark VARCHAR(255), preview_pic_urls VARCHAR(512), property TEXT, {common})""")
    query(connection, f"""CREATE TABLE promotion_diy_page (
            id BIGINT PRIMARY KEY, template_id BIGINT, name VARCHAR(64), remark VARCHAR(255),
            preview_pic_urls VARCHAR(512), property TEXT, {common})""")
    query(connection, """INSERT INTO promotion_diy_template(id,name,used,property,tenant_id)
            VALUES (900001,'CI station template',1,%s,1)""",
          (json.dumps({'tabBar': {'items': [], 'theme': 'orange'}}),))
    page = json.dumps({'style': {'backgroundColor': '#f7f7f7'}, 'components': [], 'data': []})
    query(connection, """INSERT INTO promotion_diy_page(id,template_id,name,property,tenant_id)
            VALUES (900001,900001,%s,%s,1),(900002,900001,%s,%s,1)""", ('首页', page, '我的', page))
    for table, columns in [
        ('pay_wallet', 'user_id BIGINT, user_type INT, balance INT DEFAULT 0, freeze_price INT DEFAULT 0, total_expense INT DEFAULT 0, total_recharge INT DEFAULT 0'),
        ('trade_cart', 'user_id BIGINT, spu_id BIGINT, sku_id BIGINT, count INT, selected BIT'),
        ('trade_order', 'user_id BIGINT, status INT, comment_status BIT'),
        ('promotion_coupon', 'user_id BIGINT, status INT'),
        ('trade_after_sale', 'user_id BIGINT, status INT'),
    ]:
        query(connection, f'CREATE TABLE {table} (id BIGINT PRIMARY KEY AUTO_INCREMENT, {columns}, {common})')
    for member_id, mobile in PHONES.items():
        query(connection, """INSERT INTO member_user(id,mobile,password,status,nickname,tenant_id)
                VALUES (%s,%s,%s,%s,%s,1)""",
              (member_id, mobile, password_hash, 1 if member_id == DISABLED else 0,
               'CI Station' if member_id == AGENT else f'CI Member {member_id}'))
    assert query(connection, "UPDATE system_users SET password=%s,status=0 WHERE username='admin' AND tenant_id=1",
                 (password_hash,)) == 1
    query(connection, "UPDATE system_tenant SET expire_time='2099-01-01',status=0 WHERE id=1")
    query(connection, "UPDATE cps_split_rule SET status=0 WHERE tenant_id=1")
    query(connection, """INSERT INTO cps_split_rule
            (rule_name,platform_share_rate,self_rebate_rate,parent_rebate_rate,grandparent_rebate_rate,
             freeze_days,status,priority,tenant_id)
            VALUES ('CI single station',0.20,0.75,0.25,0,0,1,9999,1)""")
    connection.close()
    print('Prepared synthetic members and station rules in the marked disposable database.')


def api(path, token=None, payload=None, method=None, tenant=1, allow_failure=False):
    require_isolated(BASE, os.environ.get('CPS_STATION_ISOLATED'))
    headers = {'Content-Type': 'application/json', 'tenant-id': str(tenant), 'terminal': '10'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = Request(BASE + path, headers=headers,
                      data=None if payload is None else json.dumps(payload).encode(),
                      method=method or ('POST' if payload is not None else 'GET'))
    try:
        with urlopen(request, timeout=30) as response:
            result = json.load(response)
    except HTTPError as error:
        result = json.loads(error.read())
    if not allow_failure and result.get('code') != 0:
        raise AssertionError(f'{path}: code={result.get("code")}, message={result.get("msg")}')
    return result


def login(member_id):
    result = api('/app-api/member/auth/login',
                 payload={'mobile': PHONES[member_id], 'password': os.environ['CPS_STATION_PASSWORD']})
    assert result['data']['userId'] == member_id
    return result['data']['accessToken']


def balance(token, expected):
    account = api('/app-api/cps/rebate/account', token)['data']
    actual = tuple(Decimal(str(account.get(key) or 0)) for key in ['availableBalance', 'frozenBalance', 'debtBalance'])
    assert actual == tuple(Decimal(str(value)) for value in expected), f'Balance mismatch: {actual} vs {expected}'
    return account


def wait_for(predicate, label, timeout=90):
    deadline = time.monotonic() + timeout
    last_error = None
    while time.monotonic() < deadline:
        try:
            value = predicate()
            if value:
                return value
        except (AssertionError, OSError) as error:
            last_error = error
        time.sleep(1)
    raise AssertionError(f'Timed out: {label}; last error={last_error}')


def exercise():
    connection = connect()
    findings = []
    assert api('/app-api/cps/rebate/account', allow_failure=True)['code'] != 0
    assert api('/app-api/member/auth/login', payload={'mobile': PHONES[DISABLED],
               'password': os.environ['CPS_STATION_PASSWORD']}, allow_failure=True)['code'] != 0
    assert api('/app-api/member/auth/login', payload={'mobile': PHONES[BUYER],
               'password': 'wrong-password'}, allow_failure=True)['code'] != 0
    findings.append('Unauthenticated, disabled-account and wrong-password requests rejected')
    admin = api('/admin-api/system/auth/login', payload={
        'username': 'admin', 'password': os.environ['CPS_STATION_PASSWORD']})['data']['accessToken']
    agent, buyer, other = login(AGENT), login(BUYER), login(OTHER)
    for path in ['/pay/wallet/get', '/trade/cart/list', '/trade/order/get-count', '/promotion/coupon/get-unused-count']:
        api('/app-api' + path, buyer)
    findings.append('Login follow-up wallet, cart, order count and coupon count endpoints succeeded')
    station = api('/app-api/cps/promoter/summary', agent)['data']
    competitor = api('/app-api/cps/promoter/summary', other)['data']
    invitation = {'inviteCode': station['inviteCode'], 'memberId': AGENT}
    assert api('/app-api/cps/promoter/bind', buyer, invitation)['data'] is True
    assert api('/app-api/cps/promoter/bind', buyer, invitation)['data'] is True
    assert api('/app-api/cps/promoter/bind', buyer, {'inviteCode': competitor['inviteCode']})['data'] is False
    assert api('/app-api/cps/promoter/summary', buyer)['data']['parentMemberId'] == AGENT
    assert api('/app-api/cps/promoter/summary', agent)['data']['directTeamCount'] == 1
    assert api('/app-api/cps/promoter/summary', other)['data']['directTeamCount'] == 0
    assert api('/app-api/cps/rebate/account', buyer, tenant=2, allow_failure=True)['code'] != 0
    findings.append('Real authenticated invitation binding, replay, competing station and tenant checks passed')
    boot = api('/admin-api/cps/rebate-asset/policy/bootstrap', admin, {}, method='POST')['data']
    assert boot['migrationReport']['ready'] is True
    api('/admin-api/cps/rebate-asset/policy/confirm-migration', admin,
        {'approvalRef': 'CI-ISOLATED-STATION-' + os.environ['CPS_STATION_NONCE'][:12]})
    api('/admin-api/cps/rebate-asset/policy', admin, {'v2Enabled': True, 'readOnly': False,
        'largeDebtThresholdCent': 10000, 'reminderIntervalDays': 7, 'normalReminderDays': 30,
        'largeReminderDays': 180, 'smsIntervalDays': 30}, method='PUT')
    balance(agent, (0, 0, 0))
    balance(buyer, (0, 0, 0))
    job = api('/admin-api/infra/job/create', admin, {
        'name': 'CI station settlement only', 'handlerName': 'cpsRebateSettleJob', 'handlerParam': '{"batchSize":10}',
        'cronExpression': '0 0 0 1 1 ? 2099', 'retryCount': 0, 'retryInterval': 0})['data']
    # Synthetic upstream settlement event only; attribution and accounting use real services.
    query(connection, """INSERT INTO cps_order(id,platform_code,platform_order_id,member_id,
            item_id,item_title,final_price,commission_amount,order_status,settle_time,confirm_receipt_time,
            attribution_source,special_id,tenant_id)
            VALUES (900001,'taobao','CI_ONLY_STATION_ORDER',%s,'CI_ITEM','CI Station Order',
                    10.00,1.00,'settled',NOW(),NOW(),'SPECIAL_ID','CI_ONLY_SPECIAL',1)""", (BUYER,))
    api(f'/admin-api/infra/job/trigger?id={job}', admin, method='PUT')
    def credited():
        return (api('/app-api/cps/rebate/account', buyer)['data'].get('availableBalance') == 0.6 and
                api('/app-api/cps/rebate/account', agent)['data'].get('availableBalance') == 0.2)
    wait_for(credited, 'buyer 0.60 and station 0.20 credited from 1.00 commission')
    balance(buyer, ('0.60', 0, 0))
    balance(agent, ('0.20', 0, 0))
    assert api('/app-api/cps/rebate/account?memberId=900001', buyer)['data']['memberId'] == BUYER
    before = query(connection, 'SELECT COUNT(*) AS n FROM cps_rebate_asset_ledger WHERE tenant_id=1')[0]['n']
    api(f'/admin-api/infra/job/trigger?id={job}', admin, method='PUT')
    time.sleep(3)
    after = query(connection, 'SELECT COUNT(*) AS n FROM cps_rebate_asset_ledger WHERE tenant_id=1')[0]['n']
    assert before == after
    findings.append('Synthetic settled commission 1.00 -> buyer 0.60 + station 0.20 + platform remainder 0.20; replay unchanged')
    api('/admin-api/cps/rebate-record/reverse?orderId=900001', admin, method='POST')
    balance(buyer, (0, 0, 0))
    balance(agent, (0, 0, 0))
    before = query(connection, 'SELECT COUNT(*) AS n FROM cps_rebate_asset_ledger WHERE tenant_id=1')[0]['n']
    api('/admin-api/cps/rebate-record/reverse?orderId=900001', admin, method='POST', allow_failure=True)
    assert query(connection, 'SELECT COUNT(*) AS n FROM cps_rebate_asset_ledger WHERE tenant_id=1')[0]['n'] == before
    findings.append('Buyer and station refund reversal returned balances to zero; repeated refund created no ledger entries')
    ledger = query(connection, """SELECT member_id,business_type,available_change_cent,frozen_change_cent,
            debt_change_cent FROM cps_rebate_asset_ledger WHERE tenant_id=1 ORDER BY id""")
    report = {'mode': 'isolated-mysql-redis-real-http-synthetic-order', 'passed': findings,
              'ledger': ledger, 'not_tested': ['real affiliate provider', 'real payment', 'physical phone scan']}
    (WORK / 'evidence/journey.json').write_text(json.dumps(report, indent=2, ensure_ascii=False, default=str), encoding='utf-8')
    connection.close()
    print('PASS: real login, invitation, MySQL settlement, ledger idempotency and refund HTTP journey')



def capture_diagnostics():
    try:
        connection = connect()
        report = {}
        for label, sql in {
            'orders': "SELECT id,member_id,order_status,rebate_freeze_status,real_rebate,rebate_settle_last_error FROM cps_order WHERE id=900001",
            'accounts': "SELECT member_id,available_balance,frozen_balance,debt_balance FROM cps_rebate_account WHERE member_id BETWEEN 900001 AND 900005",
            'rebates': "SELECT id,member_id,order_id,split_role,rebate_amount,rebate_status FROM cps_rebate_record WHERE order_id=900001",
            'ledgers': "SELECT member_id,business_type,available_change_cent,frozen_change_cent,debt_change_cent FROM cps_rebate_asset_ledger WHERE member_id BETWEEN 900001 AND 900005",
        }.items():
            report[label] = query(connection, sql)
        (WORK / 'evidence/failure-state.json').write_text(json.dumps(report, indent=2, default=str), encoding='utf-8')
        print('Sanitized synthetic-order diagnostics captured')
        connection.close()
    except Exception:
        print('Diagnostics unavailable; no additional mutations attempted')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['init', 'prepare', 'exercise'])
    action = parser.parse_args().action
    try:
        {'init': init_workspace, 'prepare': prepare, 'exercise': exercise}[action]()
    except Exception:
        if action == 'exercise':
            capture_diagnostics()
        raise
