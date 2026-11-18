#!/usr/bin/env python3
"""Prepare official pinned model assets, or validate their bytes inside an APK."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'app/src/models/assets/models/catalog.json'
DEST = ROOT / 'app/build/bundled-assets/models'


def verify(stream, pin, length):
    if length != pin['bytes']:
        raise SystemExit(f"{pin['file']}: expected {pin['bytes']} bytes, got {length}")
    if hashlib.file_digest(stream, 'sha256').hexdigest() != pin['sha256']:
        raise SystemExit(f"{pin['file']}: SHA-256 mismatch")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, help='verify a packaged APK instead of downloading')
    args = parser.parse_args()
    catalog = json.loads(CATALOG.read_text())
    if args.apk:
        with zipfile.ZipFile(args.apk) as apk:
            assert apk.read('assets/models/catalog.json') == CATALOG.read_bytes(), 'APK catalog differs from source'
            for name, pin in catalog.items():
                asset = f'assets/models/{name}.litertlm'
                info = apk.getinfo(asset)
                assert info.compress_type == zipfile.ZIP_STORED, f'{asset}: asset must be stored uncompressed'
                with apk.open(info) as stream:
                    verify(stream, pin, info.file_size)
                print(f'Verified {asset}: {info.file_size} bytes and pinned SHA-256.')
        return

    DEST.mkdir(parents=True, exist_ok=True)
    for name, pin in catalog.items():
        assert re.fullmatch(r'litert-community/[\w.-]+', pin['repository'])
        assert re.fullmatch(r'[0-9a-f]{40}', pin['revision'])
        assert re.fullmatch(r'[\w.-]+\.litertlm', pin['file'])
        assert re.fullmatch(r'[a-z0-9.-]+', name) and not name.startswith('.')
        dest = DEST / f'{name}.litertlm'
        if dest.is_file():
            with dest.open('rb') as existing:
                verify(existing, pin, dest.stat().st_size)
            continue
        url = f"https://huggingface.co/{pin['repository']}/resolve/{pin['revision']}/{pin['file']}?download=true"
        partial = dest.with_suffix('.partial')
        try:
            subprocess.run(['curl', '--fail', '--silent', '--show-error', '--location',
                            '--proto', '=https', '--proto-redir', '=https', '--retry', '3',
                            '--retry-all-errors', '--connect-timeout', '20', '--max-time', '240',
                            '--max-filesize', str(pin['bytes']), '--output', str(partial), url], check=True)
            with partial.open('rb') as model:
                verify(model, pin, partial.stat().st_size)
            partial.replace(dest)
            print(f'Prepared {name} ({pin["bytes"]} bytes).', flush=True)
        finally:
            partial.unlink(missing_ok=True)


if __name__ == '__main__':
    main()
