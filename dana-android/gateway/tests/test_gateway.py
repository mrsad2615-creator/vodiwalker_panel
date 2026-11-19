import asyncio
import base64
from contextlib import AsyncExitStack
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
import uuid

import httpx

from mrsad_gateway import Config, build_app

AUTH = "https://abcdefghijklmnopqrst.supabase.co"
KEY = "sb_publishable_public_example_123456"
USER = str(uuid.UUID(int=5))
SECOND = str(uuid.UUID(int=6))
BEARER = "Bearer verified-fixture-token"
TEST_CHATTY = "example/text-pro"
PLAN = {"action": "wifi_reminder", "seconds": 150, "on": False}
PNG = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jH4sAAAAASUVORK5CYII=")


class GatewayTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.stack = AsyncExitStack()
        self.addAsyncCleanup(self.stack.aclose)
        self.directory = self.stack.enter_context(TemporaryDirectory())
        self.provider_calls = []
        self.mode = "ok"
        self.config = {
            "MRSAD_AUTH_ORIGIN": AUTH, "MRSAD_AUTH_PUBLISHABLE_KEY": KEY, "MRSAD_PUBLIC_SIGNUP": "true",
            "MRSAD_DB_PATH": str(Path(self.directory, "usage.sqlite3")),
            "MRSAD_TEXT_MODELS": json.dumps([
                {"id": TEST_CHATTY, "title": "Text Pro", "provider": "Example"},
                {"id": "other/large", "title": "Large", "provider": "Other"},
            ]),
            "MRSAD_PLANNER_MODEL": TEST_CHATTY, "MRSAD_OPENROUTER_KEY": "operator-only-router-key",
            "MRSAD_IMAGE_MODELS": "openai-sunburst,avalai-sunburst,gemini-pro",
            "MRSAD_OPENAI_KEY": "operator-only-openai-key", "MRSAD_AVALAI_KEY": "operator-only-avalai-key",
            "MRSAD_GOOGLE_KEY": "operator-only-google-key",
        }

    async def client(self, **overrides):
        app = build_app(Config.from_env({**self.config, **overrides}), httpx.MockTransport(self.upstream))
        await self.stack.enter_async_context(app.router.lifespan_context(app))
        return await self.stack.enter_async_context(httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="https://managed.example.test"))

    async def upstream(self, request):
        url = str(request.url)
        token = request.headers.get("authorization")
        if url == AUTH + "/auth/v1/user":
            self.assertEqual(request.method, "GET")
            self.assertEqual(request.headers["apikey"], KEY)
            if token == "Bearer forged-fixture-token":
                return httpx.Response(401, json={"message": "should not escape"})
            return httpx.Response(200, json={"id": SECOND if token == "Bearer second-fixture-token" else USER,
                                            "role": "authenticated", "email": "test@example.test",
                                            "email_confirmed_at": None if token == "Bearer unverified-token" else "2026-11-17"})
        self.assertNotEqual(token, BEARER)
        self.assertNotIn("apikey", request.headers)
        self.assertNotIn("idempotency-key", request.headers)
        payload = json.loads(await request.aread())
        self.provider_calls.append((url, payload, token))
        if self.mode == "redirect":
            return httpx.Response(307, headers={"location": "https://redirected.example.test/secret"})
        if url == "https://openrouter.ai/api/v1/chat/completions":
            self.assertEqual(token, "Bearer operator-only-router-key")
            answer = self.mode if self.mode != "ok" else (
                json.dumps(PLAN) if payload.get("response_format") else "پاسخ مدل")
            return httpx.Response(200, json={"choices": [{"finish_reason": "stop", "message": {"content": answer}}]})
        if request.url.host == "generativelanguage.googleapis.com":
            self.assertIsNone(token)
            self.assertEqual(request.headers["x-goog-api-key"], "operator-only-google-key")
            return httpx.Response(200, json={"candidates": [{"content": {"parts": [
                {"inlineData": {"data": base64.b64encode(PNG).decode(), "mimeType": "image/png"}},
            ]}}]})
        self.assertIn(request.url.host, {"api.openai.com", "api.avalai.ir"})
        expected = "openai" if request.url.host == "api.openai.com" else "avalai"
        self.assertEqual(token, f"Bearer operator-only-{expected}-key")
        return httpx.Response(200, json={"data": [{"b64_json": base64.b64encode(PNG).decode()}]})

    def headers(self, bearer=BEARER, ident=None):
        return {"Authorization": bearer, "Idempotency-Key": ident or str(uuid.uuid4())}

    async def send_text(self, client, **kwargs):
        return await client.post("/v1/chat", headers=kwargs.pop("headers", self.headers()),
                                 json={"model": TEST_CHATTY, "prompt": "یک سؤال از متن", "history": [], **kwargs})

    async def test_single_verified_token_unlocks_only_enabled_catalog_and_chat(self):
        client = await self.client()
        self.assertEqual(401, (await client.get("/v1/catalog")).status_code)
        for token in ("Bearer forged-fixture-token", "Bearer unverified-token"):
            self.assertEqual(401, (await self.send_text(client, headers=self.headers(token))).status_code)
        catalog = await client.get("/v1/catalog", headers=self.headers())
        self.assertEqual(200, catalog.status_code)
        self.assertEqual(5, len(catalog.json()["models"]))
        self.assertEqual(TEST_CHATTY, catalog.json()["planner"])
        self.assertEqual("no-store", catalog.headers["cache-control"])
        self.assertNotIn("operator-only", catalog.text)
        self.assertEqual([], self.provider_calls)
        self.assertEqual(404, (await self.send_text(client, model="not/allowed")).status_code)
        self.assertEqual(400, (await self.send_text(client, endpoint="https://elsewhere.example.test")).status_code)
        reply = await self.send_text(client, model="other/large", history=[{"role": "user", "content": "قبلی"}, {"role": "assistant", "content": "پاسخ قبلی"}])
        self.assertEqual({"text": "پاسخ مدل"}, reply.json())
        _, posted, _ = self.provider_calls[0]
        self.assertEqual("other/large", posted["model"])
        self.assertFalse(posted["provider"]["allow_fallbacks"])
        self.assertEqual(["system", "user", "assistant", "user"], [turn["role"] for turn in posted["messages"]])
        self.assertNotIn("email", json.dumps(posted))

    async def test_pilot_membership_is_checked_after_auth_before_upstream(self):
        client = await self.client(MRSAD_PUBLIC_SIGNUP="false", MRSAD_MEMBER_IDS=SECOND)
        self.assertEqual(403, (await self.send_text(client)).status_code)
        self.assertFalse(self.provider_calls)
        self.assertEqual(200, (await self.send_text(client, headers=self.headers("Bearer second-fixture-token"))).status_code)

    async def test_same_email_session_can_use_each_enabled_image_without_provider_login(self):
        client = await self.client(MRSAD_ACCOUNT_DAILY_UNITS="20")
        for model in ("openai-sunburst", "avalai-sunburst", "gemini-pro"):
            reply = await client.post("/v1/image", headers=self.headers(), json={"model": model, "prompt": "کوه مه‌آلود", "shape": "landscape"})
            self.assertEqual((200, "image/png", PNG), (reply.status_code, reply.headers["content-type"], reply.content))
        for _, payload, _ in self.provider_calls[:2]:
            self.assertEqual(("gpt-image-2.6-sunburst", "1536x1024", "high"), (payload["model"], payload["size"], payload["quality"]))
        url, google, _ = self.provider_calls[2]
        self.assertIn("gemini-3-pro-image:generateContent", url)
        self.assertEqual({"aspectRatio": "3:2", "imageSize": "2K"}, google["generationConfig"]["imageConfig"])
        self.assertEqual(5, (await client.get("/v1/catalog", headers=self.headers())).json()["daily_remaining"])

    async def test_command_output_is_strict_and_model_changes_require_new_review(self):
        client = await self.client()
        async def command(model=TEST_CHATTY):
            return await client.post("/v1/command", headers=self.headers(), json={"model": model, "prompt": "وای‌فای را پس از دو و نیم دقیقه قطع کن"})
        self.assertEqual(404, (await command("other/large")).status_code)
        self.assertFalse(self.provider_calls)
        self.assertEqual(PLAN, (await command()).json())
        for bad in ({"action": "shell", "command": "not-an-allowed-effect"}, {"action": "timer", "seconds": True},
                    {"action": "wifi", "on": "false"}, {**PLAN, "uri": "https://example.test"}, {"action": "timer", "seconds": 0}):
            self.mode = json.dumps(bad)
            self.assertEqual(502, (await command()).status_code)
        self.mode = '{"action":"timer","seconds":90,"seconds":100}'
        self.assertEqual(502, (await command()).status_code)

    async def test_attempts_are_atomically_limited_deduplicated_and_never_retried(self):
        client = await self.client(MRSAD_ACCOUNT_DAILY_UNITS="2", MRSAD_TOTAL_DAILY_UNITS="2")
        key = self.headers()
        replies = await asyncio.gather(*(self.send_text(client, headers=key) for _ in range(4)))
        self.assertEqual([200, 409, 409, 409], sorted(reply.status_code for reply in replies))
        replies = await asyncio.gather(*(self.send_text(client) for _ in range(5)))
        self.assertEqual([200, 429, 429, 429, 429], sorted(reply.status_code for reply in replies))
        second = self.headers("Bearer second-fixture-token")
        self.assertEqual(429, (await self.send_text(client, headers=second)).status_code)
        self.assertEqual(0, (await client.get("/v1/catalog", headers=second)).json()["daily_remaining"])
        self.assertEqual(2, len(self.provider_calls))
        raw = b"".join(file.read_bytes() for file in Path(self.directory).glob("*.sqlite3*"))
        for forbidden in ("یک سؤال از متن", "پاسخ مدل", "verified-fixture-token", "test@example.test"):
            self.assertNotIn(forbidden.encode(), raw)

    async def test_redirect_and_oversized_requests_are_not_forwarded(self):
        client = await self.client()
        self.mode = "redirect"
        key = self.headers()
        blocked = await self.send_text(client, headers=key)
        self.assertEqual(503, blocked.status_code)
        self.assertNotIn("redirected", blocked.text)
        self.assertEqual(409, (await self.send_text(client, headers=key)).status_code)
        self.assertEqual(413, (await self.send_text(client, prompt="الف" * 50_000)).status_code)
        self.assertEqual(1, len(self.provider_calls))

    async def test_uses_the_same_utf16_payload_limit_as_android(self):
        client = await self.client()
        self.assertEqual(200, (await self.send_text(client, prompt="🎨" * 1_200)).status_code)
        self.assertEqual(400, (await self.send_text(client, prompt="🎨" * 1_201)).status_code)
        self.assertEqual(1, len(self.provider_calls))

    def test_catalog_cannot_enable_a_route_without_key_or_choose_an_automatic_model(self):
        with self.assertRaises(ValueError):
            Config.from_env({**self.config, "MRSAD_GOOGLE_KEY": ""})
        with self.assertRaises(ValueError):
            Config.from_env({**self.config, "MRSAD_TEXT_MODELS": '[{"id":"openrouter/auto","title":"Auto","provider":"Unknown"}]'})


if __name__ == "__main__":
    unittest.main()
