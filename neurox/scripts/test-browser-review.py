#!/usr/bin/env python3
"""Run both browser reviewers against a bounded local static server.

Requires bootstrap-design.sh and the Kotlin-generated learning-content.json.
The server is closed before exit. Nothing is uploaded by this script.
"""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import json
import os
import subprocess
import threading

PROJECT = Path(__file__).resolve().parents[1]
ROOT = PROJECT.parent


def main():
    if not (PROJECT / "test-output/learning-content.json").is_file():
        raise SystemExit("Generate test-output/learning-content.json with NEUROX_CONTENT_REVIEW_OUT first")
    for script in ["design/prototype/prototype.js", "design/content-review/review.js"]:
        subprocess.run(["node", "--check", str(PROJECT / script)], check=True)

    class QuietHandler(SimpleHTTPRequestHandler):
        def log_message(self, *_args):
            pass

    # Bind before starting the browser. No fixed-port race or sleep loop.
    server = ThreadingHTTPServer(("0.0.0.0", 0), partial(QuietHandler, directory=str(PROJECT)))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    env = os.environ.copy()
    origin = f"http://127.0.0.1:{server.server_port}"
    env.update(DESIGN_URL=origin, CONTENT_REVIEW_URL=origin)
    libraries = str(ROOT / ".local/tools/design/sysroot/lib")
    env["LD_LIBRARY_PATH"] = libraries + (":" + env["LD_LIBRARY_PATH"] if env.get("LD_LIBRARY_PATH") else "")
    try:
        for script in ["test-design.mjs", "test-content-review.mjs"]:
            subprocess.run(["node", str(PROJECT / "scripts" / script)], cwd=ROOT, env=env, check=True, timeout=180)
        prototype = json.loads((PROJECT / "test-output/design/report.json").read_text())
        content = json.loads((PROJECT / "test-output/content-review/report.json").read_text())
        print("::notice title=Browser review counts::"
              f"Prototype layouts={prototype['checks']}; "
              f"content layouts={content['layoutChecks']}; "
              f"content axe audits={content['audits']}; "
              f"answered draft questions={content['answered']}. "
              "Browser checks only; not editorial approval or Android accessibility acceptance.")
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)


if __name__ == "__main__":
    main()
