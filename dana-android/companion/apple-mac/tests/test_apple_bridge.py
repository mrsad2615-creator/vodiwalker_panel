"""Protocol/security tests. Apple hardware and model inference aren't simulated as availability evidence."""

import argparse
import asyncio
from contextlib import aclosing
import json
import os
from pathlib import Path
import socket
import ssl
import subprocess
import sys
import tempfile
import unittest

from apple_bridge import (
    AppleBridge, MODEL, MAX_REQUEST_BYTES, encode, error, private_v4, read_token, tls_config, worker_events,
)

TOKEN = b"dana-test-bridge-token-0123456789-abcd"
AUTH = [(b"authorization", b"Bearer " + TOKEN)]
JSON = [(b"content-type", b"application/json; charset=utf-8")]
PAYLOAD = {"model": MODEL, "prompt": "A technical question", "instructions": "Answer in English.", "max_output_tokens": 128}


class Exchange:
    def __init__(self, app, method="GET", path="/v1/models", body=b"", headers=None, scheme="https", query=b""):
        self.inbox = asyncio.Queue()
        self.outbox = []
        self.inbox.put_nowait({"type": "http.request", "body": body})
        scope = {"type": "http", "scheme": scheme, "headers": AUTH if headers is None else headers,
                 "method": method, "path": path, "query_string": query}
        self.task = asyncio.create_task(app(scope, self.inbox.get, self.send))

    async def send(self, message):
        self.outbox.append(message)

    async def finish(self):
        await asyncio.wait_for(self.task, timeout=3)
        self.assert_single_http_response()
        return self.outbox[0]["status"], b"".join(message.get("body", b"") for message in self.outbox)

    def assert_single_http_response(self):
        assert self.outbox[0]["type"] == "http.response.start"
        assert sum(message["type"] == "http.response.start" for message in self.outbox) == 1
        assert self.outbox[-1]["type"] == "http.response.body"
        assert not self.outbox[-1].get("more_body", False)


class ProtocolTest(unittest.IsolatedAsyncioTestCase):
    async def test_authentication_and_origin_rejection_do_not_invoke_worker(self):
        async def must_not_run(_):
            self.fail("Unauthorized requests reached the backend")
            yield

        app = AppleBridge(TOKEN, must_not_run)
        for overrides, expected in [
            ({"headers": []}, 401), ({"headers": AUTH + AUTH}, 401),
            ({"headers": [(b"authorization", b"Bearer invalid-token")]}, 401),
            ({"headers": AUTH + [(b"origin", b"https://some-site.example")]}, 403), ({"scheme": "http"}, 403),
            ({"query": b"token=not-supported"}, 400), ({"method": "OPTIONS"}, 405),
            ({"path": "/v1/anything-else"}, 404),
        ]:
            with self.subTest(overrides=overrides):
                status, _ = await Exchange(app, **overrides).finish()
                self.assertEqual(status, expected)

    async def test_rejects_malformed_text_invalid_model_and_oversized_body(self):
        async def must_not_run(_):
            self.fail("Invalid input reached worker")
            yield

        app = AppleBridge(TOKEN, must_not_run)
        for body, expected in [
            (b"not-json", 400), (encode({**PAYLOAD, "model": "wrong"}), 400),
            (encode({**PAYLOAD, "prompt": "\x00"}), 400), (encode({**PAYLOAD, "max_output_tokens": True}), 400),
            (encode({**PAYLOAD, "prompt": "😀" * 3_001}), 400), (b"a" * (MAX_REQUEST_BYTES + 1), 413),
            (b"[" * 2_000 + b"]" * 2_000, 400),
        ]:
            with self.subTest(expected=expected, size=len(body)):
                status, _ = await Exchange(app, "POST", "/v1/respond", body, AUTH + JSON).finish()
                self.assertEqual(expected, status)
        self.assertEqual(415, (await Exchange(app, "POST", "/v1/respond", encode(PAYLOAD)).finish())[0])

    async def test_catalog_is_authenticated_readiness_without_a_prompt(self):
        async def backend(payload):
            self.assertEqual({"list": True}, payload)
            yield {"ready": True}

        status, data = await Exchange(AppleBridge(TOKEN, backend)).finish()
        self.assertEqual(200, status)
        self.assertEqual(MODEL, json.loads(data)["data"][0]["id"])

    async def test_unavailable_model_is_never_advertised(self):
        async def backend(_):
            yield error("apple_unavailable")

        status, data = await Exchange(AppleBridge(TOKEN, backend)).finish()
        self.assertEqual(503, status)
        self.assertEqual(error("apple_unavailable"), json.loads(data))

    async def test_snapshot_revisions_and_terminal_in_sse(self):
        async def backend(payload):
            self.assertEqual({k: v for k, v in PAYLOAD.items() if k != "model"}, payload)
            for frame in [{"ready": True}, {"snapshot": "First"}, {"snapshot": "Revised ✨"}, {"done": True}]:
                yield frame

        exchange = Exchange(AppleBridge(TOKEN, backend), "POST", "/v1/respond", encode(PAYLOAD), AUTH + JSON)
        status, data = await exchange.finish()
        self.assertEqual(200, status)
        self.assertIn((b"content-type", b"text/event-stream; charset=utf-8"), exchange.outbox[0]["headers"])
        self.assertEqual([{"snapshot": "First"}, {"snapshot": "Revised ✨"}, {"done": True}],
                         [json.loads(s.removeprefix(b"data: ")) for s in data.split(b"\n\n") if s])

    async def test_truncated_or_rejected_worker_is_never_reported_as_success(self):
        for failing in [None, "unsupported_language", "error with a secret path"]:
            async def backend(_):
                yield {"ready": True}
                yield {"snapshot": "Partial"}
                if failing:
                    yield error(failing)

            status, data = await Exchange(AppleBridge(TOKEN, backend), "POST", "/v1/respond", encode(PAYLOAD), AUTH + JSON).finish()
            self.assertEqual(200, status)  # Already streaming; the terminal error is in SSE.
            self.assertNotIn(b'"done":true', data)
            self.assertNotIn(b"secret path", data)
            self.assertTrue(data.endswith(encode(error(failing if failing == "unsupported_language" else "internal")) + b"\n\n"))

    async def test_disconnect_ends_work_and_clears_the_one_request_gate(self):
        entered = asyncio.Event()
        canceled = asyncio.Event()

        async def backend(payload):
            yield {"ready": True}
            if not payload.get("list"):
                try:
                    entered.set()
                    await asyncio.Future()
                finally:
                    canceled.set()

        app = AppleBridge(TOKEN, backend)
        first = Exchange(app, "POST", "/v1/respond", encode(PAYLOAD), AUTH + JSON)
        await asyncio.wait_for(entered.wait(), 3)
        self.assertEqual(429, (await Exchange(app).finish())[0])
        first.inbox.put_nowait({"type": "http.disconnect"})
        await asyncio.wait_for(first.task, 3)
        self.assertTrue(canceled.is_set())
        self.assertEqual(200, (await Exchange(app).finish())[0])

    async def test_closing_pipe_reaps_actual_worker_process(self):
        program = "import json,os,sys,time; sys.stdin.buffer.read(); print(json.dumps({'pid':os.getpid()}),flush=True); time.sleep(60)"
        async with aclosing(worker_events({"list": True}, (sys.executable, "-I", "-c", program))) as frames:
            frame = await asyncio.wait_for(anext(frames), 4)
            pid = frame["pid"]
            os.kill(pid, 0)
        with self.assertRaises(ProcessLookupError):
            os.kill(pid, 0)

    async def test_real_tls_server_refuses_unauthenticated_request(self):
        # Real sockets and certificate/hostname verification; no prompt or model execution.
        from hypercorn.asyncio import serve

        with tempfile.TemporaryDirectory() as directory:
            cert, key = (Path(directory) / name for name in ("cert.pem", "key.pem"))
            subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
                            "-subj", "/CN=localhost", "-addext", "subjectAltName=DNS:localhost",
                            "-keyout", str(key), "-out", str(cert)], check=True, capture_output=True)
            with socket.socket() as reservation:
                reservation.bind(("127.0.0.1", 0))
                port = reservation.getsockname()[1]
            config = tls_config("127.0.0.1", port, cert, key)
            self.assertEqual(ssl.TLSVersion.TLSv1_2, config.create_ssl_context().minimum_version)
            stop = asyncio.Event()
            server = asyncio.create_task(serve(AppleBridge(TOKEN), config, shutdown_trigger=stop.wait))
            try:
                client_tls = ssl.create_default_context(cafile=str(cert))
                for _ in range(50):
                    if server.done():
                        server.result()
                    try:
                        reader, writer = await asyncio.open_connection("127.0.0.1", port, ssl=client_tls, server_hostname="localhost")
                        break
                    except ConnectionRefusedError:
                        await asyncio.sleep(0.03)
                else:
                    self.fail("TLS server did not accept a connection")
                try:
                    writer.write(b"GET /v1/models HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                    await writer.drain()
                    reply = await asyncio.wait_for(reader.read(), 4)
                    self.assertIn(b"401", reply.splitlines()[0])
                    self.assertIn(b"invalid_token", reply)
                    self.assertNotIn(TOKEN, reply)
                finally:
                    writer.close()
                    await writer.wait_closed()
            finally:
                stop.set()
                await asyncio.wait_for(server, 5)


class ConfigurationTest(unittest.TestCase):
    def test_private_bind_and_file_permissions(self):
        for address in ("0.0.0.0", "8.8.8.8", "172.2.3.4", "::", "mac.local"):
            self.assertRaises(argparse.ArgumentTypeError, private_v4, address)
        self.assertEqual("192.168.2.3", private_v4("192.168.2.3"))
        with tempfile.TemporaryDirectory() as directory:
            token_path = Path(directory) / "token"
            token_path.write_bytes(TOKEN + b"\n")
            token_path.chmod(0o644)
            self.assertRaises(ValueError, read_token, token_path)
            token_path.chmod(0o600)
            self.assertEqual(TOKEN, read_token(token_path))
            symlink = Path(directory) / "symlink"
            symlink.symlink_to(token_path)
            self.assertRaises(OSError, read_token, symlink)


if __name__ == "__main__":
    unittest.main()
