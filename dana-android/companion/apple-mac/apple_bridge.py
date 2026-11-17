"""Private HTTPS adapter for Apple's on-device Foundation Models, not an Apple cloud API."""

from __future__ import annotations

import argparse
import asyncio
from contextlib import aclosing
import hmac
import ipaddress
import json
import os
from pathlib import Path
import platform
import re
import stat
import subprocess
import sys

MODEL = "apple-foundation-on-device"
MAX_REQUEST_BYTES = 49_152
MAX_EVENT_BYTES = 113_000
MAX_ANSWER_CHARS = 18_000  # UTF-16 units, matching Android.
TOKEN = re.compile(rb"[A-Za-z0-9_-]{32,128}")
PRIVATE_V4 = tuple(map(ipaddress.IPv4Network, ("127.0.0.0/8", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")))
ERROR_CODES = frozenset({
    "apple_unavailable", "unsupported_language", "context_length_exceeded",
    "apple_refusal", "apple_busy", "output_limit", "internal", "timeout",
})
HTTP_HEADERS = [(b"cache-control", b"no-store"), (b"x-content-type-options", b"nosniff")]


def encode(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def error(code):
    return {"error": {"code": code}}


def utf16_size(text):
    # Reject embedded NUL (the Apple bindings use C strings) and unpaired surrogates.
    if not isinstance(text, str) or "\0" in text:
        raise ValueError("Invalid text")
    return len(text.encode("utf-16-le")) // 2


class Rejected(Exception):
    def __init__(self, status, code):
        self.status = status
        self.code = code


class Disconnected(Exception):
    pass


async def receive_body(receive):
    body = bytearray()
    while True:
        event = await receive()
        if event["type"] == "http.disconnect":
            raise Disconnected
        if event["type"] != "http.request":
            raise Rejected(400, "invalid_request")
        chunk = event.get("body", b"")
        if len(chunk) > MAX_REQUEST_BYTES - len(body):
            raise Rejected(413, "request_too_large")
        body.extend(chunk)
        if not event.get("more_body", False):
            return bytes(body)


def parse_prompt(body):
    try:
        request = json.loads(body.decode("utf-8"))
        if not isinstance(request, dict) or request.keys() != {"model", "prompt", "instructions", "max_output_tokens"}:
            raise ValueError
        prompt, instructions, tokens = (request[k] for k in ("prompt", "instructions", "max_output_tokens"))
        if (request["model"] != MODEL or not 1 <= utf16_size(prompt) <= 6_000 or not prompt.strip()
                or not 1 <= utf16_size(instructions) <= 1_024 or type(tokens) is not int or not 1 <= tokens <= 1_024):
            raise ValueError
        return {"prompt": prompt, "instructions": instructions, "max_output_tokens": tokens}
    except (ValueError, RecursionError) as exc:
        raise Rejected(400, "invalid_request") from exc


async def send_message(send, event):
    try:
        await asyncio.wait_for(send(event), timeout=6)
    except (OSError, TimeoutError) as exc:
        raise Disconnected from exc


async def send_json(send, status, value):
    body = encode(value)
    await send_message(send, {"type": "http.response.start", "status": status, "headers": [
        *HTTP_HEADERS, (b"content-type", b"application/json; charset=utf-8"),
        (b"content-length", str(len(body)).encode("ascii")),
    ]})
    await send_message(send, {"type": "http.response.body", "body": body})


async def send_frame(send, value):
    await send_message(send, {"type": "http.response.body", "body": b"data: " + encode(value) + b"\n\n", "more_body": True})


def checked_error(event):
    detail = event.get("error") if isinstance(event, dict) else None
    if not isinstance(detail, dict):
        return "internal"
    code = detail.get("code")
    return code if isinstance(code, str) and code in ERROR_CODES else "internal"


async def worker_events(payload, command=None):
    """Keep the SDK's blocking native iterator out of the ASGI event loop and in a killable process."""
    if command is None:
        command = (sys.executable, "-I", str(Path(__file__).resolve()), "--worker")
    process = await asyncio.create_subprocess_exec(
        *command, stdin=asyncio.subprocess.PIPE, stdout=asyncio.subprocess.PIPE,
        stderr=subprocess.DEVNULL, limit=MAX_EVENT_BYTES,
    )
    try:
        process.stdin.write(encode(payload))
        await process.stdin.drain()
        process.stdin.close()
        while line := await process.stdout.readline():
            if len(line) > MAX_EVENT_BYTES:
                raise ValueError("Invalid worker frame")
            yield json.loads(line)
        if await process.wait() != 0:
            raise RuntimeError("Apple worker stopped unexpectedly")
    finally:
        # The SDK may be blocked in a native callback; do not wait for generation on a lost connection.
        if process.returncode is None:
            try:
                process.kill()
            except ProcessLookupError:
                pass
            await process.wait()


class AppleBridge:
    def __init__(self, token, backend=worker_events):
        if not TOKEN.fullmatch(token):
            raise ValueError("Use a random URL-safe bridge token of 32–128 characters")
        self._authorization = b"Bearer " + token
        self._backend = backend
        self._busy = False

    async def __call__(self, scope, receive, send):
        if scope["type"] == "lifespan":
            while (event := await receive())["type"] != "lifespan.shutdown":
                if event["type"] == "lifespan.startup":
                    await send({"type": "lifespan.startup.complete"})
            await send({"type": "lifespan.shutdown.complete"})
            return
        if scope["type"] == "websocket":
            await send({"type": "websocket.close", "code": 1008})
            return
        try:
            await self.handle(scope, receive, send)
        except Rejected as exc:
            try:
                await send_json(send, exc.status, error(exc.code))
            except Disconnected:
                pass
        except Disconnected:
            pass

    async def handle(self, scope, receive, send):
        headers = scope["headers"]
        if scope["scheme"] != "https" or any(name == b"origin" for name, _ in headers):
            raise Rejected(403, "private_https_only")
        auth = [value for name, value in headers if name == b"authorization"]
        if len(auth) != 1 or not hmac.compare_digest(self._authorization, auth[0]):
            raise Rejected(401, "invalid_token")
        if scope.get("query_string"):
            raise Rejected(400, "query_not_supported")
        path = scope["path"]
        if path not in {"/v1/respond", "/v1/models"}:
            raise Rejected(404, "not_found")
        listing = path == "/v1/models"
        if scope["method"] != ("GET" if listing else "POST"):
            raise Rejected(405, "method_not_allowed")
        if not listing:
            content_types = [value.split(b";", 1)[0].strip().lower() for name, value in headers if name == b"content-type"]
            if content_types != [b"application/json"]:
                raise Rejected(415, "expected_json")
        try:
            body = await asyncio.wait_for(receive_body(receive), timeout=8)
        except TimeoutError as exc:
            raise Rejected(408, "timeout") from exc
        if listing and body:
            raise Rejected(400, "invalid_request")
        payload = {"list": True} if listing else parse_prompt(body)
        # No shared transcript, no queue, no parallel model contexts.
        if self._busy:
            raise Rejected(429, "apple_busy")
        self._busy = True
        inference = asyncio.create_task(self.run(payload, listing, send))
        disconnect = asyncio.create_task(self.wait_disconnect(receive))
        try:
            finished, _ = await asyncio.wait({inference, disconnect}, return_when=asyncio.FIRST_COMPLETED)
            if inference in finished:
                inference.result()
        finally:
            inference.cancel()
            disconnect.cancel()
            try:
                await asyncio.gather(inference, disconnect, return_exceptions=True)
            finally:
                self._busy = False

    @staticmethod
    async def wait_disconnect(receive):
        while (await receive())["type"] != "http.disconnect":
            pass

    async def run(self, request, listing, send):
        started = False
        terminal = None
        try:
            async with asyncio.timeout(80), aclosing(self._backend(request)) as frames:
                first = await anext(frames, None)
                if first != {"ready": True}:
                    code = checked_error(first)
                    status = {"apple_unavailable": 503, "apple_busy": 429, "timeout": 504}.get(code, 500)
                    await send_json(send, status, error(code))
                    return
                if listing:
                    await send_json(send, 200, {"data": [{"id": MODEL, "displayName": "Apple Foundation Models"}]})
                    return
                await send_message(send, {"type": "http.response.start", "status": 200, "headers": [
                    *HTTP_HEADERS, (b"content-type", b"text/event-stream; charset=utf-8"),
                ]})
                started = True
                async for frame in frames:
                    if frame == {"done": True}:
                        terminal = frame
                        break
                    if isinstance(frame, dict) and "error" in frame:
                        terminal = error(checked_error(frame))
                        break
                    if (not isinstance(frame, dict) or set(frame) != {"snapshot"}
                            or utf16_size(frame["snapshot"]) > MAX_ANSWER_CHARS):
                        raise ValueError("Invalid model snapshot")
                    await send_frame(send, frame)
        except Disconnected:
            raise
        except TimeoutError:
            if not started:
                await send_json(send, 504, error("timeout"))
                return
            terminal = error("timeout")
        except Exception:
            # Never return SDK exceptions, request data, or local paths to clients.
            if not started:
                await send_json(send, 500, error("internal"))
                return
            terminal = error("internal")
        await send_frame(send, terminal or error("internal"))
        await send_message(send, {"type": "http.response.body", "body": b""})


def worker_frame(value):
    sys.stdout.buffer.write(encode(value) + b"\n")
    sys.stdout.buffer.flush()


async def apple_worker(request):
    try:
        import apple_fm_sdk as fm

        model = fm.SystemLanguageModel()
        available, _reason = model.is_available()
    except Exception:
        worker_frame(error("internal"))
        return
    if not available:
        worker_frame(error("apple_unavailable"))
        return
    worker_frame({"ready": True})
    if request.get("list"):
        return
    try:
        session = fm.LanguageModelSession(model=model, instructions=request["instructions"])
        options = fm.GenerationOptions(maximum_response_tokens=request["max_output_tokens"])
        last = None
        async for snapshot in session.stream_response(request["prompt"], options=options):
            if utf16_size(snapshot) > MAX_ANSWER_CHARS or len(encode({"snapshot": snapshot})) >= MAX_EVENT_BYTES:
                worker_frame(error("output_limit"))
                return
            if snapshot != last:
                worker_frame({"snapshot": snapshot})
                last = snapshot
        worker_frame({"done": True})
    except fm.UnsupportedLanguageOrLocaleError:
        worker_frame(error("unsupported_language"))
    except fm.ExceededContextWindowSizeError:
        worker_frame(error("context_length_exceeded"))
    except (fm.GuardrailViolationError, fm.RefusalError):
        worker_frame(error("apple_refusal"))
    except fm.AssetsUnavailableError:
        worker_frame(error("apple_unavailable"))
    except (fm.RateLimitedError, fm.ConcurrentRequestsError):
        worker_frame(error("apple_busy"))
    except Exception:
        worker_frame(error("internal"))


def private_v4(raw):
    try:
        address = ipaddress.IPv4Address(raw)
        if any(address in block for block in PRIVATE_V4):
            return str(address)
    except ipaddress.AddressValueError:
        pass
    raise argparse.ArgumentTypeError("Use a specific private IPv4 address or loopback; do not bind a public or wildcard address.")


def read_token(path):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, "rb") as source:
        info = os.fstat(fd)
        if info.st_uid != os.getuid() or info.st_mode & 0o077 or not stat.S_ISREG(info.st_mode):
            raise ValueError("Token file must be private (chmod 600), regular, and owned by your account.")
        token = source.read(131).strip(b"\r\n")
        if not TOKEN.fullmatch(token):
            raise ValueError("Invalid token file. Generate a private URL-safe token as described in the guide.")
        return token


def tls_config(bind, port, cert, key):
    from hypercorn.config import Config

    config = Config()
    config.bind = [f"{private_v4(bind)}:{port}"]
    config.certfile = str(cert)
    config.keyfile = str(key)
    config.alpn_protocols = ["http/1.1"]
    config.accesslog = None
    config.errorlog = None
    config.keep_alive_timeout = 5
    config.read_timeout = 96  # Must outlast an active response; ASGI separately caps body reads at 8 s.
    config.h11_max_incomplete_size = 16_384
    config.include_server_header = False
    config.create_ssl_context()
    return config


def cli():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind", type=private_v4, default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--cert", required=True, type=Path, help="PEM full chain for this HTTPS hostname")
    parser.add_argument("--key", required=True, type=Path, help="Private key for the certificate")
    parser.add_argument("--token-file", required=True, type=Path)
    args = parser.parse_args()
    if platform.system() != "Darwin" or platform.machine() != "arm64" or int(platform.mac_ver()[0].split(".")[0]) < 26:
        parser.error("Apple silicon and macOS 26 or later are required; Apple Intelligence must be enabled.")
    if not 1_024 <= args.port <= 65_535:
        parser.error("Use an unprivileged TCP port (1024–65535).")
    try:
        from hypercorn.asyncio import serve

        bridge = AppleBridge(read_token(args.token_file))
        key = args.key.stat()
        if not stat.S_ISREG(key.st_mode) or key.st_mode & 0o077 or key.st_uid != os.getuid():
            raise ValueError("The TLS key file must be private (chmod 600) and owned by your account.")
        config = tls_config(args.bind, args.port, args.cert, args.key)
        print(f"DANA Apple bridge: TLS on {args.bind}:{args.port}; no model or readiness claim yet.", flush=True)
        asyncio.run(serve(bridge, config))
    except (ImportError, OSError, ValueError) as exc:
        parser.exit(1, f"Bridge could not start: {exc}\n")


if __name__ == "__main__":
    if sys.argv[1:] == ["--worker"]:
        raw = sys.stdin.buffer.read(MAX_REQUEST_BYTES + 1)
        if len(raw) > MAX_REQUEST_BYTES:
            raise SystemExit(1)
        asyncio.run(apple_worker(json.loads(raw)))
    else:
        cli()
