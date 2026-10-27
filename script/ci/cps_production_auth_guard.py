#!/usr/bin/env python3
"""Static production-auth gate. Does not send SMS or contact any external service."""
from pathlib import Path
import json
import re
import yaml

ROOT=Path(__file__).resolve().parents[2]
PROD=ROOT/"backend/qiji-server/src/main/resources/application-prod.yaml"
COMMON=ROOT/"backend/qiji-server/src/main/resources/application.yaml"
SERVICE=ROOT/"backend/qiji-module-system/src/main/java/com/qiji/cps/module/system/service/sms/SmsCodeServiceImpl.java"
REPORT=ROOT/"production-auth-config-report.json"
checks={}

prod_docs=[v for v in yaml.safe_load_all(PROD.read_text(encoding="utf-8")) if isinstance(v,dict)]
def qiji_config(doc):
    return doc.get("qiji") if isinstance(doc.get("qiji"),dict) else {}
sms_overrides=[qiji_config(d).get("sms-code") for d in prod_docs
               if isinstance(qiji_config(d).get("sms-code"),dict)]
checks["production_otp_profile_explicit"]=len(sms_overrides)==1
otp=sms_overrides[-1] if sms_overrides else {}
begin=otp.get("begin-code")
end=otp.get("end-code")
checks["unpredictable_six_digit_range"]=(
    isinstance(begin,int) and isinstance(end,int) and
    begin>=100000 and end<=999999 and (end-begin)>=800000
)
security_docs=[qiji_config(d) for d in prod_docs if isinstance(qiji_config(d).get("security"),dict)]
checks["production_mock_auth_disabled"]=bool(security_docs) and all(
    q["security"].get("mock-enable") is False for q in security_docs)
checks["production_captcha_enabled"]=any(
    qiji_config(d).get("captcha",{}).get("enable") is True
    for d in prod_docs if isinstance(qiji_config(d).get("captcha"),dict)
)
source=SERVICE.read_text(encoding="utf-8")
checks["sms_service_uses_range_from_properties"]=(
    "smsCodeProperties.getBeginCode()" in source and
    "smsCodeProperties.getEndCode() + 1" in source and
    "randomInt(" in source
)
common=COMMON.read_text(encoding="utf-8")
checks["common_test_range_is_not_used_as_prod_override"]=(
    "begin-code: 9999" in common and
    not (begin==9999 or end==9999)
)
report={"checks":checks,
    "production_default_otp_range":[begin,end] if isinstance(begin,int) and isinstance(end,int) else None,
    "production_ready":False,
    "note":"Static file verification; new profile must be rebuilt and deployed before running cloud behavior changes."}
REPORT.write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
print("AUTH_PROD_STATIC_CHECKS",json.dumps(checks,sort_keys=True),flush=True)
if not all(checks.values()):
    raise SystemExit("Production authentication configuration is unsafe/incomplete")
print("PRODUCTION_SMS_OTP_STATIC_GUARD=PASS",flush=True)
