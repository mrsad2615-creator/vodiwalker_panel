#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
make.py — ساخت دو فایل ZIP نهایی (هر کدام زیر ۱۰ گیگ) برای PPSSPP
===================================================================

    python3 make.py

کارهایی که انجام می‌دهد، به ترتیب:

  1. اگر links.txt پر باشد → لینک‌ها را دانلود می‌کند (با ادامه‌ی دانلود نیمه‌کاره).
  2. هر فایل ISO/CSO/CHD/7z/ZIP/RAR که در inbox/ باشد را می‌شکافد و ISO/CSO را برمی‌دارد.
  3. هر فایل را با اسمش به بازی درست نسبت می‌دهد (data/selection.json).
  4. حجم واقعی را می‌سنجد و حریصانه پر می‌کند تا از ۹.۷ گیگ رد نشود.
  5. دقیقاً دو فایل می‌سازد:

         out/PSP_1_NOSTALGIA.zip
         out/PSP_2_GRAPHICS.zip

     داخل هر ZIP: پوشه‌ی PSP/ با فایل‌های بازی + یک CHECK.txt کوتاه.

سوییچ‌ها:

    --plan      فقط نقشه را نشان بده، چیزی نساز
    --fresh     قبل از ساخت، out/ قدیمی را پاک کن
    --cap 9.7   سقف حجم هر ZIP به گیگابایت
    --no-down   فایل‌ها را دانلود نکن (فقط از inbox بخوان)
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "data" / "selection.json"
INBOX = ROOT / "inbox"
WORK = ROOT / "work"
OUT = ROOT / "out"
LINKS = ROOT / "links.txt"
CACHE = ROOT / ".cache_dl"

ROM_EXT = {".iso", ".cso", ".chd"}
ARC_EXT = {".zip", ".7z", ".rar", ".tar", ".gz"}
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122 Safari/537.36"
GB = 1024**3
MB = 1024**2


# ───────────────────────────────────────────────────────────── helpers
def gb(n: int | float) -> str:
    n = float(n)
    return f"{n / GB:.2f} GB" if n >= GB else f"{n / MB:.0f} MB"


def norm(s: str) -> str:
    out = []
    for ch in s.lower():
        out.append(ch if ch.isalnum() else " ")
    return " ".join("".join(out).split())


STOP = {
    "the", "of", "a", "and", "on", "in", "to", "for", "psp", "usa", "eur", "europe",
    "japan", "iso", "cso", "chd", "zip", "7z", "rar", "proper", "multi5", "multi3",
    "v1", "v2", "v3", "rev", "en", "fr", "es", "de", "it", "e", "u", "j", "readnfo",
}


def toks(s: str) -> set[str]:
    return {t for t in norm(s).split() if t not in STOP}


def score(filename: str, game: dict) -> float:
    """انطباق اسم فایل با بازی: بین ۰ و ۱."""
    f = norm(filename)
    keys = [norm(game["title"])] + [norm(q) for q in game.get("queries", [])]
    for k in keys:
        if k and k in f:
            return 1.0
    tt = toks(game["title"]) | set().union(*(toks(q) for q in game.get("queries", [])))
    ft = set(norm(filename).split())
    if not tt or not ft:
        return 0.0
    hit = len(tt & ft)
    return hit / len(tt)


def pick_game(filename: str, games: list[dict]) -> tuple[dict | None, float]:
    best, best_s = None, 0.0
    for g in games:
        s = score(filename, g)
        if s > best_s:
            best, best_s = g, s
    return (best, best_s) if best_s >= 0.55 else (None, best_s)


def human_time(sec: float) -> str:
    m, s = divmod(int(sec), 60)
    h, m = divmod(m, 60)
    return f"{h}:{m:02d}:{s:02d}" if h else f"{m:02d}:{s:02d}"


# ───────────────────────────────────────────────────────────── download
def download(url: str, dest_dir: Path, max_bytes: int = 3 * GB) -> Path | None:
    """دانلود با ادامه‌پذیری، تلاش مجدد و نوار پیشرفت. None = شکست."""
    dest_dir.mkdir(parents=True, exist_ok=True)
    name = url.split("?")[0].rstrip("/").split("/")[-1] or f"file_{int(time.time())}"
    if "." not in name:
        name += ".iso"
    part = dest_dir / (name + ".part")
    final = dest_dir / name

    if final.exists() and final.stat().st_size > 0:
        print(f"   ↺ از قبل موجود: {final.name} ({gb(final.stat().st_size)})")
        return final

    for attempt in range(1, 4):
        have = part.stat().st_size if part.exists() else 0
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        if have:
            req.add_header("Range", f"bytes={have}-")
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                clen = int(r.headers.get("Content-Length") or 0)
                if have and r.status == 206:
                    mode, total = "ab", clen + have
                else:
                    # سرور Range را پشتیبانی نمی‌کند → از صفر
                    mode, total, have = "wb", clen, 0
                if total and total > max_bytes:
                    print(f"   ✗ خیلی بزرگ ({gb(total)})، رد شد: {url[:70]}")
                    return None
                t0, last, session = time.time(), 0.0, 0
                with open(part, mode) as fh:
                    while True:
                        chunk = r.read(1024 * 512)
                        if not chunk:
                            break
                        fh.write(chunk)
                        have += len(chunk)
                        session += len(chunk)
                        now = time.time()
                        if now - last > 1.0:
                            last = now
                            pct = f" ({have * 100 / total:.0f}%)" if total else ""
                            spd = session / max(now - t0, 0.001) / MB
                            print(
                                f"\r   ↓ {gb(have)}{pct} · {spd:.1f} MB/s   ",
                                end="",
                                flush=True,
                            )
            print()
            if total and part.stat().st_size != total:
                raise IOError(f"ناقص: {part.stat().st_size} از {total}")
            part.replace(final)
            return final
        except urllib.error.HTTPError as e:
            print(f"   ! HTTP {e.code} (تلاش {attempt}/3): {url[:70]}")
        except Exception as e:  # noqa: BLE001
            print(f"   ! {type(e).__name__}: {e} (تلاش {attempt}/3)")
        time.sleep(2 * attempt)
    print(f"   ✗ نشد: {url[:80]}")
    return None


def read_links() -> list[str]:
    if not LINKS.exists():
        return []
    out = []
    for line in LINKS.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith(("http://", "https://")):
            out.append(line)
    return out


# ───────────────────────────────────────────────────────────── extract
def extract(path: Path) -> list[Path]:
    """آرشیو را باز می‌کند و فایل‌های ROM داخلش را برمی‌گرداند."""
    ext = path.suffix.lower()
    target = WORK / path.stem
    if ext == ".zip":
        with zipfile.ZipFile(path) as z:
            z.extractall(target)
    elif ext == ".7z":
        try:
            import py7zr  # type: ignore

            with py7zr.SevenZipFile(path) as z:
                z.extractall(target)
        except ImportError:
            exe = shutil.which("7z") or shutil.which("7za") or shutil.which("7zr")
            if not exe:
                print(f"   ! برای .7z نصب کن: pip install py7zr  (یا 7z)")
                return []
            subprocess.run([exe, "x", "-y", f"-o{target}", str(path)], check=True)
    elif ext == ".rar":
        exe = shutil.which("unrar") or shutil.which("bsdtar") or shutil.which("7z")
        if not exe:
            print("   ! برای .rar نصب کن: unrar  (یا 7z)")
            return []
        cmd = [exe, "x", "-y", str(path), str(target) + os.sep]
        if exe.endswith("bsdtar"):
            cmd = [exe, "-xf", str(path), "-C", str(target)]
        target.mkdir(parents=True, exist_ok=True)
        subprocess.run(cmd, check=False)
    else:
        return [path] if ext in ROM_EXT else []

    return [p for p in target.rglob("*") if p.is_file() and p.suffix.lower() in ROM_EXT]


def gather(no_download: bool) -> list[Path]:
    """همه‌ی فایل‌های ROM را از links.txt و inbox/ جمع می‌کند."""
    roms: list[Path] = []

    if not no_download:
        urls = read_links()
        if urls:
            print(f"\n▸ دانلود {len(urls)} لینک از links.txt")
            for i, u in enumerate(urls, 1):
                print(f" [{i}/{len(urls)}] {u[:90]}")
                got = download(u, CACHE)
                if got:
                    roms.append(got)
        else:
            print("\n▸ links.txt خالی است — فقط از inbox/ می‌خوانم")

    for p in sorted(INBOX.rglob("*")):
        if not p.is_file():
            continue
        if p.name.startswith("."):
            continue
        ext = p.suffix.lower()
        if ext in ROM_EXT:
            roms.append(p)
        elif ext in ARC_EXT:
            print(f"▸ باز کردن آرشیو: {p.name} ({gb(p.stat().st_size)})")
            roms.extend(extract(p))

    for p in sorted(CACHE.rglob("*")):
        if p.is_file() and p.suffix.lower() in ARC_EXT:
            print(f"▸ باز کردن آرشیو: {p.name}")
            roms.extend(extract(p))

    # حذف تکراری‌ها بر اساس حجم+نام
    seen, uniq = set(), []
    for p in roms:
        try:
            key = (p.name.lower(), p.stat().st_size)
        except OSError:
            continue
        if key in seen:
            continue
        seen.add(key)
        uniq.append(p)
    return uniq


# ───────────────────────────────────────────────────────────── pack
def build_plan(roms: list[Path], data: dict, cap_gb: float) -> dict[str, list[tuple[dict, Path]]]:
    games = data["games"]
    cap = int(cap_gb * GB)
    assigned: dict[str, list[tuple[dict, Path]]] = {k: [] for k in data["folders"]}
    unknown = []

    for p in roms:
        g, s = pick_game(p.stem, games)
        if not g:
            unknown.append(p)
            continue
        assigned[g["folder"]].append((g, p))

    if unknown:
        print("\n⚠ این فایل‌ها به هیچ بازی‌ای نسبت داده نشدند (نادیده گرفته شدند):")
        for u in unknown:
            print(f"   - {u.name}  ({gb(u.stat().st_size)})")

    plan: dict[str, list[tuple[dict, Path]]] = {}
    for fid, items in assigned.items():
        items.sort(key=lambda x: -x[1].stat().st_size)
        keep, total, dropped = [], 0, []
        for g, p in items:
            size = p.stat().st_size
            if total + size <= cap:
                keep.append((g, p))
                total += size
            else:
                dropped.append((g, p))
        keep.sort(key=lambda x: x[0]["title"].lower())
        plan[fid] = keep
        if dropped:
            print(f"\n▸ سقف {cap_gb} گیگ پر شد — این‌ها جا نشدند:")
            for g, p in dropped:
                print(f"   ✗ {g['title']} ({gb(p.stat().st_size)})")
    return plan


def write_zip(fid: str, items: list[tuple[dict, Path]], data: dict, fresh: bool) -> Path | None:
    if not items:
        print(f"\n▸ {data['folders'][fid]['name_fa']}: خالی — ZIP ساخته نشد")
        return None
    meta = data["folders"][fid]
    OUT.mkdir(exist_ok=True)
    target = OUT / f"{meta['zip_name']}.zip"
    if target.exists():
        if not fresh:
            print(f"\n▸ از قبل هست، رد شد: {target.name} ({gb(target.stat().st_size)})")
            return target
        target.unlink()

    total = sum(p.stat().st_size for _, p in items)
    print(f"\n▸ ساخت {target.name}  ({len(items)} بازی، {gb(total)})")

    tmp = target.with_suffix(".zip.tmp")
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_STORED, allowZip64=True) as z:
        for g, p in items:
            arc = f"PSP/{p.name}"
            z.write(p, arcname=arc)
            print(f"   + {p.name}  ({gb(p.stat().st_size)})")
        check = "\n".join(
            [f"PPSSPP — {meta['name_fa']}", f"{len(items)} games / {gb(total)}", ""]
            + [f"{i:2d}. {p.name}" for i, (_, p) in enumerate(items, 1)]
        )
        z.writestr("CHECK.txt", check + "\n")
    tmp.replace(target)
    size = target.stat().st_size
    flag = "✓" if size <= 10 * GB else "⚠ از ۱۰ گیگ گذشت!"
    print(f"   {flag} {target}  ({gb(size)})")
    return target


def wipe_outs() -> None:
    for d in (OUT, WORK):
        if d.exists():
            shutil.rmtree(d)
    OUT.mkdir(parents=True, exist_ok=True)


# ───────────────────────────────────────────────────────────── main
def main() -> None:
    ap = argparse.ArgumentParser(description="ساخت دو ZIP نهایی PPSSPP")
    ap.add_argument("--plan", action="store_true", help="فقط نقشه")
    ap.add_argument("--fresh", action="store_true", help="خروجی‌های قبلی را پاک کن")
    ap.add_argument("--cap", type=float, default=9.7, help="سقف هر ZIP (گیگ)")
    ap.add_argument("--no-down", action="store_true", help="دانلود نکن")
    args = ap.parse_args()

    data = json.loads(DATA.read_text(encoding="utf-8"))
    for d in (INBOX, WORK, OUT, CACHE):
        d.mkdir(parents=True, exist_ok=True)

    if args.fresh:
        wipe_outs()

    print("=" * 62)
    print(" ساخت دو ZIP کالکشن PPSSPP")
    print("=" * 62)

    roms = gather(args.no_down)
    if not roms:
        print("\nهیچ فایل بازی‌ای پیدا نشد.")
        print(" → لینک‌ها را در links.txt بگذار، یا فایل‌ها را در inbox/ بریز.")
        print(" → بعد دوباره: python3 make.py")
        return

    print(f"\n▸ {len(roms)} فایل ROM پیدا شد ({gb(sum(p.stat().st_size for p in roms))})")
    plan = build_plan(roms, data, args.cap)

    print("\n" + "-" * 62)
    for fid, items in plan.items():
        name = data["folders"][fid]["name_fa"]
        tot = sum(p.stat().st_size for _, p in items)
        print(f"{name:>10}: {len(items):2d} بازی · {gb(tot)}")
    print("-" * 62)

    if args.plan:
        return

    outs = [write_zip(fid, items, data, args.fresh) for fid, items in plan.items()]

    print("\n" + "=" * 62)
    print(" تمام:")
    for o in outs:
        if o:
            print(f"   {o.name}  —  {gb(o.stat().st_size)}")
    print("=" * 62)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nمتوقف شد. دانلودهای نیمه‌کاره در .cache_dl/ می‌مانند و دفعه‌ی بعد ادامه می‌یابند.")
