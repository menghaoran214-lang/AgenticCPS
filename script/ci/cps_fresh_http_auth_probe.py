#!/usr/bin/env python3
"""Real HTTP authorization probes against a disposable, GitHub-hosted CPS stack.

Runs only with the station CI database nonce/loopback guards. No production host,
no external SMS/OAuth, and no real credentials. Evidence is aggregate only.
"""
import json
import os
from station_journey import (
    AGENT, BUYER, DISABLED, PHONES, WORK, api, connect, login,
    require_isolated, BASE,
)
from station_hp_preflight import github_hosted


def rejected(path, *, token=None, payload=None, tenant=1, method=None):
    response = api(path, token=token, payload=payload, tenant=tenant,
                   method=method, allow_failure=True)
    code = response.get("code")
    # A server-side 500 is NOT a successful denial of an unauthenticated request.
    assert isinstance(code, int) and code not in (0, 500), (path, "missing business rejection")
    return True


def main():
    if not github_hosted() or os.environ.get("CPS_STATION_PROFILE") != "ci":
        raise RuntimeError("Only GitHub-hosted station CI is allowed")
    require_isolated(BASE, os.environ.get("CPS_STATION_ISOLATED"))
    connection = connect()  # Enforces the randomized database marker.
    try:
        checks = {}
        checks["anonymous_rebate_account_rejected"] = rejected(
            "/app-api/cps/rebate/account")
        checks["member_wrong_password_rejected"] = rejected(
            "/app-api/member/auth/login", payload={
                "mobile": PHONES[BUYER], "password": "ci-wrong-password"})
        checks["disabled_member_login_rejected"] = rejected(
            "/app-api/member/auth/login", payload={
                "mobile": PHONES[DISABLED], "password": os.environ["CPS_STATION_PASSWORD"]})
        member_token = login(BUYER)
        checks["member_login_and_own_account"] = (
            api("/app-api/cps/rebate/account", token=member_token).get("code") == 0)
        checks["member_cannot_get_admin_permission_info"] = rejected(
            "/admin-api/system/auth/get-permission-info", token=member_token)
        checks["tenant_header_mismatch_denied"] = rejected(
            "/app-api/cps/rebate/account", token=member_token, tenant=2)
        admin_token = api("/admin-api/system/auth/login", payload={
            "username": "admin", "password": os.environ["CPS_STATION_PASSWORD"]})["data"]["accessToken"]
        checks["admin_permissions_accessible"] = (
            api("/admin-api/system/auth/get-permission-info", token=admin_token).get("code") == 0)
        checks["admin_token_rejected_as_member"] = rejected(
            "/app-api/cps/rebate/account", token=admin_token)
        checks["invalid_refresh_token_rejected"] = rejected(
            "/app-api/member/auth/refresh-token?refreshToken=ci-nonexistent-token", method="POST")
        checks["member_logout_success"] = (
            api("/app-api/member/auth/logout", token=member_token, method="POST").get("code") == 0)
        checks["revoked_member_token_rejected"] = rejected(
            "/app-api/cps/rebate/account", token=member_token)
        checks["admin_logout_success"] = (
            api("/admin-api/system/auth/logout", token=admin_token, method="POST").get("code") == 0)
        checks["revoked_admin_token_rejected"] = rejected(
            "/admin-api/system/auth/get-permission-info", token=admin_token)
        with connection.cursor() as cur:
            cur.execute("""SELECT COUNT(*) AS total,
                SUM(deleted=b'0') AS active,
                SUM(deleted=b'1') AS revoked
                FROM system_oauth2_access_token WHERE tenant_id=1""")
            token_summary = cur.fetchone()
        # Access tokens are logically deleted; COUNT(*) includes audit history.
        checks["oauth_active_tokens_revoked_in_mysql"] = int(token_summary["active"] or 0) == 0
        checks["oauth_revocation_history_preserved"] = int(token_summary["revoked"] or 0) >= 2
        passed = all(checks.values())
        report = {
            "mode": "real HTTP Spring Boot and MySQL/Redis, GitHub-hosted disposable fixture",
            "checks": checks,
            "test_count": len(checks),
            "passed": passed,
            "source_artifact": "built from the exact checked-out PR commit in this workflow",
            "mocked_or_not_tested": [
                "External SMS and social OAuth",
                "User registration through real SMS provider",
                "Token natural expiry / clock advancement",
                "Public HTTP/HTTPS or Tencent runtime",
                "Payment and withdrawals",
            ],
            "cloud_deployment_verified": False,
            "go_live_approved": False,
        }
        (WORK / "evidence/auth-http.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
        print("AUTH_HTTP_CHECKS", json.dumps(checks, sort_keys=True), flush=True)
        if not passed:
            raise AssertionError("HTTP authorization gate failed; see sanitized AUTH_HTTP_CHECKS")
        print("PASS: disposable HTTP login, user type isolation, tenant mismatch, logout and DB revocation")
    finally:
        connection.close()


if __name__ == "__main__":
    main()
