"""Join real local GoTrue OTP to the production gateway code; only model providers are fixtures."""

import asyncio
import base64
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import uuid

import httpx

from mrsad_gateway import Config, build_app

PROJECT = "https://abcdefghijklmnopqrst.supabase.co"
PUBLISHABLE = "sb_publishable_local_integration_only_2026"
MODEL = "fixture/text"
PNG = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jH4sAAAAASUVORK5CYII=")


def check_managed(auth_origin, user_id, access):
    asyncio.run(journey(auth_origin, user_id, access))


async def journey(auth_origin, user_id, access):
    calls = []
    async with httpx.AsyncClient(base_url=auth_origin, trust_env=False, follow_redirects=False, timeout=5) as auth:
        async def upstream(request):
            url = str(request.url)
            if url == PROJECT + "/auth/v1/user":
                assert request.headers.get("apikey") == PUBLISHABLE
                # Map only this known host to the disposable local auth container (no arbitrary URL proxying).
                return await auth.get("/user", headers={"Authorization": request.headers["Authorization"]})
            assert access not in str(request.headers) and access.encode() not in request.content
            assert "apikey" not in request.headers
            if url == "https://openrouter.ai/api/v1/chat/completions":
                assert request.headers.get("authorization") == "Bearer fixture-router-key"
                assert json.loads(request.content)["model"] == MODEL
                calls.append("chat")
                return httpx.Response(200, json={"choices": [{"message": {"content": "آزمایش ورود موفق"}, "finish_reason": "stop"}]})
            if url == "https://api.openai.com/v1/images/generations":
                assert request.headers.get("authorization") == "Bearer fixture-image-key"
                calls.append("image")
                return httpx.Response(200, json={"data": [{"b64_json": base64.b64encode(PNG).decode()}]})
            raise AssertionError("Unexpected fixture destination")

        with TemporaryDirectory() as directory:
            config = Config.from_env({"MRSAD_AUTH_ORIGIN": PROJECT, "MRSAD_AUTH_PUBLISHABLE_KEY": PUBLISHABLE,
                                      "MRSAD_MEMBER_IDS": user_id, "MRSAD_OPENROUTER_KEY": "fixture-router-key",
                                      "MRSAD_OPENAI_KEY": "fixture-image-key", "MRSAD_IMAGE_MODELS": "openai-sunburst",
                                      "MRSAD_TEXT_MODELS": json.dumps([{"id": MODEL, "title": "Demo", "provider": "Fixture"}]),
                                      "MRSAD_DB_PATH": str(Path(directory, "test.sqlite3"))})
            app = build_app(config, httpx.MockTransport(upstream))
            async with app.router.lifespan_context(app), httpx.AsyncClient(transport=httpx.ASGITransport(app=app),
                                                                            base_url="https://gateway.example.test") as client:
                rejected = await client.get("/v1/catalog", headers={"Authorization": "Bearer not-a-valid-jwt"})
                assert rejected.status_code == 401 and not calls
                auth_header = {"Authorization": "Bearer " + access}
                reply = await client.get("/v1/catalog", headers=auth_header)
                assert reply.status_code == 200, f"Verified GoTrue session did not open gateway catalog: HTTP {reply.status_code}"
                assert {model["id"] for model in reply.json()["models"]} == {MODEL, "openai-sunburst"}
                headers = {**auth_header, "Idempotency-Key": str(uuid.uuid4())}
                reply = await client.post("/v1/chat", headers=headers, json={"model": MODEL, "prompt": "سلام", "history": []})
                assert (reply.status_code, reply.json()) == (200, {"text": "آزمایش ورود موفق"})
                headers = {**auth_header, "Idempotency-Key": str(uuid.uuid4())}
                reply = await client.post("/v1/image", headers=headers, json={"model": "openai-sunburst", "prompt": "باران", "shape": "square"})
                assert reply.status_code == 200 and reply.content == PNG
                assert calls == ["chat", "image"]
