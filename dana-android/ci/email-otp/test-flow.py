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


def received_code(previous_id=None):
    for _ in range(30):
        messages = require("mail list", http(f"{MAIL}/api/v1/messages"), {200}).get("messages", [])
        for msg in messages:
            if msg["ID"] == previous_id or not any(to.get("Address") == ADDRESS for to in msg.get("To", [])):
                continue
            mail = require("mail body", http(f"{MAIL}/api/v1/message/{quote(msg['ID'], safe='')}"), {200})
            found = re.search(r'id="dana-code"[^>]*>(\d{6})<', mail.get("HTML", ""))
            if found:
                return msg["ID"], found[1]
            raise SystemExit("Mail reached the sink but no six-digit OTP in the configured template")
        time.sleep(0.5)
    raise SystemExit("No verification email reached the isolated SMTP sink")


def verify(code):
    return http(f"{AUTH}/verify", {"email": ADDRESS, "token": code, "type": "email"})


def send(label):
    require(label, http(f"{AUTH}/otp", {"email": ADDRESS, "create_user": True}), {200})


def verified_user(session):
    user = session["user"]
    assert user["email"] == ADDRESS and user["email_confirmed_at"] and session["token_type"].lower() == "bearer"
    return user["id"]


send("registration OTP")
first_message, code = received_code()
wrong = "000000" if code != "000000" else "111111"
require("incorrect OTP", verify(wrong), {400, 403, 422})
registered = require("verify registration OTP", verify(code), {200})
user_id = verified_user(registered)
require("used OTP cannot be replayed", verify(code), {400, 403, 422})
logout_path = f"{AUTH}/logout?scope=local"
require("sign out before logging in again", http(logout_path, {}, registered["access_token"]), {200, 204})

# Both requests match Android's create_user=true flow. The second distinct email must open the existing account.
time.sleep(1.1)
send("login OTP")
_, new_code = received_code(previous_id=first_message)
verified = require("verify login OTP", verify(new_code), {200})
assert verified_user(verified) == user_id, "Existing email did not reopen the same account"
refresh_path = f"{AUTH}/token?grant_type=refresh_token"
rotated = require("refresh", http(refresh_path, {"refresh_token": verified["refresh_token"]}), {200})
assert rotated["refresh_token"] != verified["refresh_token"]
require("revoke this session", http(logout_path, {}, rotated["access_token"]), {200, 204})
require("revoked refresh rejected", http(refresh_path, {"refresh_token": rotated["refresh_token"]}), {400, 401, 403, 422})
print("Real GoTrue: registration and login emails delivered to SMTP sink; incorrect and reused codes rejected; session refreshed and revoked.")
