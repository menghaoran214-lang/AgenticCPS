#!/usr/bin/env python3
"""Read-only localhost smoke checks for AgenticCPS admin/H5/CPS security boundaries.

No login, secrets, orders, payouts, external vendor calls, or DB writes.
"""
import json
import sys
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

BASE = "http://127.0.0.1:18080"
TIMEOUT = 8
failures = []


def fetch(path, method="GET", payload=None):
    body = json.dumps(payload).encode("utf-8") if payload is not None else None
    request = Request(
        BASE + path,
        method=method,
        data=body,
        headers={"tenant-id": "1", "Content-Type": "application/json"},
    )
    try:
        response = urlopen(request, timeout=TIMEOUT)
    except HTTPError as exc:
        response = exc
    with response:
        return response.status, response.read(1_000_000)


def check_page(label, path):
    try:
        status, content = fetch(path)
        ok = status == 200 and bool(content)
        print(f"{'PASS' if ok else 'FAIL'} {label}: HTTP {status}")
        if not ok:
            failures.append(label)
    except (URLError, TimeoutError) as exc:
        failures.append(label)
        print(f"FAIL {label}: connection error ({type(exc).__name__})")


def check_api(label, path, expected_code, method="GET", payload=None, template=False):
    try:
        http_status, body = fetch(path, method, payload)
        parsed = json.loads(body)
        actual_code = parsed.get("code")
        ok = http_status == 200 and actual_code == expected_code
        if template:
            data = parsed.get("data")
            ok = ok and isinstance(data, dict) and bool(data.get("id"))
            ok = ok and isinstance(data.get("home"), dict) and isinstance(data.get("user"), dict)
        print(f"{'PASS' if ok else 'FAIL'} {label}: HTTP {http_status}, code={actual_code}")
        if not ok:
            failures.append(label)
    except (URLError, TimeoutError, ValueError, TypeError) as exc:
        failures.append(label)
        print(f"FAIL {label}: {type(exc).__name__}")


def main():
    check_page("admin frontend", "/index")
    check_page("H5 frontend", "/h5/")
    check_api("active H5 template", "/app-api/promotion/diy-template/used", 0, template=True)
    check_api("empty search validation", "/app-api/cps/goods/search?keyword=", 400)
    check_api("unauthenticated promotion-link protection", "/app-api/cps/goods/link", 401,
              method="POST", payload={"platformCode": "taobao", "goodsId": "readonly-smoke"})
    check_api("unauthenticated order protection", "/app-api/cps/order/page?pageNo=1&pageSize=1", 401)
    check_api("unauthenticated admin protection", "/admin-api/system/auth/get-permission-info", 401)
    print(f"RESULT: {7 - len(failures)}/7 checks passed")
    print("Scope: HTTP, validation, authentication boundary only; NOT vendor/order/rebate E2E.")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

[executed on device: VM-0-8-ubuntu (10d5c91e-d088-4f17-907c-0fe55c656dd6)]