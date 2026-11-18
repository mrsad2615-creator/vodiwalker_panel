"""Test the same OTP JSON exchange as the Android client against real GoTrue and a local SMTP sink."""

import json
import re
import time
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen

AUTH = "http://127.0.0.1:19999"  # Direct container: the hosted gateway adds /auth/v1 and validates apikey.
MAIL = "http://127.0.0.1:18025"
ADDRESS = "dana-auth-smoke@dana.example.test"


def http(url, payload=None, access=None):
    headers = {"Accept": "application/json"}
    if access:
        headers["Authorization"] = f"Bearer {access}"
    body = None if payload is None else json.dumps(payload).encode("ascii")
    if body is not None:
        headers["Content-Type"] = "application/json"
    try:
        with urlopen(Request(url, data=body, headers=headers), timeout=5) as response:
            return response.status, json.loads(response.read() or b"{}")
    except HTTPError as error:
        with error:
            return error.code, json.loads(error.read() or b"{}")


def require(label, answer, codes):
    status, result = answer
    if status not in codes:
        raise SystemExit(f"{label}: unexpected HTTP {status}; service code {result.get('error_code', 'not provided')}")
    return result


def received_code():
    for _ in range(30):
        messages = require("mail list", http(f"{MAIL}/api/v1/messages"), {200}).get("messages", [])
        for msg in messages:
            if not any(to.get("Address") == ADDRESS for to in msg.get("To", [])):
                continue
            mail = require("mail body", http(f"{MAIL}/api/v1/message/{quote(msg['ID'], safe='')}"), {200})
            found = re.search(r'id="dana-code"[^>]*>(\d{6})<', mail.get("HTML", ""))
            if found:
                return found[1]
            raise SystemExit("Mail reached the sink but no six-digit OTP in the configured template")
        time.sleep(0.5)
    raise SystemExit("No verification email reached the isolated SMTP sink")


require("send OTP", http(f"{AUTH}/otp", {"email": ADDRESS, "create_user": True}), {200})
code = received_code()
wrong = "000000" if code != "000000" else "111111"
verify_path = f"{AUTH}/verify"
require("incorrect OTP", http(verify_path, {"email": ADDRESS, "token": wrong, "type": "email"}), {400, 403, 422})
verified = require("correct OTP", http(verify_path, {"email": ADDRESS, "token": code, "type": "email"}), {200})
assert verified["user"]["email"] == ADDRESS and verified["user"]["email_confirmed_at"] and verified["token_type"].lower() == "bearer"
require("used OTP cannot be replayed", http(verify_path, {"email": ADDRESS, "token": code, "type": "email"}), {400, 403, 422})
refresh_path = f"{AUTH}/token?grant_type=refresh_token"
rotated = require("refresh", http(refresh_path, {"refresh_token": verified["refresh_token"]}), {200})
assert rotated["refresh_token"] != verified["refresh_token"]
require("revoke this session", http(f"{AUTH}/logout?scope=local", {}, rotated["access_token"]), {200, 204})
require("revoked refresh rejected", http(refresh_path, {"refresh_token": rotated["refresh_token"]}), {400, 401, 403, 422})
print("Real GoTrue: OTP delivered to local SMTP sink; incorrect and reused codes rejected; email verified, session refreshed and revoked.")
