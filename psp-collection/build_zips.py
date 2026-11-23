#!/usr/bin/env python3
"""
build_zips.py — ساخت پوشه‌بندی و آرشیو ZIP برای کالکشن PPSSPP
================================================================

کاری که این اسکریپت انجام می‌دهد:

1. فایل‌های بازی شما (.iso / .cso / .chd / .zip / .7z) را از پوشه‌ی `files/` می‌خواند.
2. با اسم فایل، هر بازی را به یکی از دو پوشه‌ی «نوستالژی» یا «گرافیک بالا» نسبت می‌دهد
   (لیست در data/games.json). اگر اسم فایل ناشناخته باشد، از شما می‌پرسد.
3. حجم *واقعی* فایل‌های موجود را حساب می‌کند و هر گروه را طوری در ZIP بریزد که
   از سقف ۱۰ گیگابایت (منهای حاشیه‌ی امن ۰.۵ گیگ) رد نشود.
4. ZIP ها را در `out/` می‌سازد؛ در حالت تقسیم‌شده، چند ZIP پیوسته (part01, part02, ...)
   که هر کدام زیر سقف هستند.

نکته‌ی مهم: ZIP کردن فایل ISO/CSO تقریباً هیچ فشرده‌سازی‌ای نمی‌دهد. پس برای صرفه‌جویی
در فضا، بهتر است خود بازی‌ها را به CSO تبدیل کنید (با `ciso.py` یا ابزار CSO Compressor)
و همان‌ها را ZIP کنید. این اسکریپت هر دو حالت را پشتیبانی می‌کند.

نمونه استفاده:

    # ۱) ببینید وضعیت چطور است و چه چیزی کم دارید
    python3 build_zips.py --report

    # ۲) ساخت آرشیوها (هر گروه زیر ۱۰ گیگ)
    python3 build_zips.py --split

    # ۳) بدون تقسیم، فقط دو ZIP (اگر مجموع زیر سقف بود)
    python3 build_zips.py

    # ۴) ساخت پوشه‌های ساده بدون ZIP (برای آپلود به درایو/تلگرام)
    python3 build_zips.py --folders-only
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "data" / "games.json"
FILES = ROOT / "files"
OUT = ROOT / "out"
GAME_EXTS = {".iso", ".cso", ".chd", ".zip", ".7z", ".rar"}


# ---------------------------------------------------------------- normalization
def norm(s: str) -> str:
    """اسم فایل/عنوان را برای مقایسه یکسان می‌کند."""
    s = s.lower()
    s = s.replace("&", "and")
    s = re.sub(r"[^a-z0-9]+", " ", s)
    return " ".join(s.split())


STOP = {
    "the", "of", "a", "and", "on", "in", "to", "for", "psp", "usa", "eur",
    "europe", "japan", "iso", "cso", "chd", "zip", "7z", "rar", "proper",
    "multi5", "multi3", "v1", "v2", "v3", "rev", "usa psn", "uhd",
}


def tokens(title: str) -> list[str]:
    """کلیدواژه‌های قابل جست‌وجو از عنوان بازی (یا اسم فایل)."""
    return [t for t in norm(title).split() if t not in STOP]


def match_score(filename: str, title: str) -> float:
    """امتیاز انطباق ۰..۱ بین اسم فایل و عنوان بازی.

    سه حالت را می‌سنجد و بهترین را برمی‌گرداند:
      • عنوان کامل داخل اسم فایل باشد            → ۱.۰
      • چه نسبتی از کلمات عنوان در فایل هست        → ۰..۱
      • چه نسبتی از کلمات فایل در عنوان هست        → ۰..۱
    حالت سوم باعث می‌شود «Lumines [PSP].iso» هم به Lumines وصل شود.
    """
    fname = norm(filename)
    ttl = norm(title)
    if ttl and (ttl in fname or fname in ttl):
        return 1.0
    tks, fks = tokens(title), tokens(filename)
    if not tks or not fks:
        return 0.0
    hits = sum(1 for t in tks if t in fks)
    return max(hits / len(tks), hits / len(fks))


# ---------------------------------------------------------------- models
@dataclass
class Game:
    id: str
    folder: str
    title: str
    size_mb: int
    files: list[Path] = field(default_factory=list)

    @property
    def size_bytes(self) -> int:
        if self.files:
            return sum(f.stat().st_size for f in self.files)
        return int(self.size_mb * 1024 * 1024)


def load_data() -> dict:
    if not DATA.exists():
        sys.exit(f"فایل داده پیدا نشد: {DATA}")
    return json.loads(DATA.read_text(encoding="utf-8"))


def build_games(data: dict) -> list[Game]:
    games = [
        Game(id=g["id"], folder=g["folder"], title=g["title"], size_mb=g["iso_mb"])
        for g in data["games"]
    ]
    return games


def scan_files(games: list[Game], ask: bool) -> list[Path]:
    """فایل‌ها را به بازی‌ها نسبت می‌دهد و لیست فایل‌های ناشناخته را برمی‌گرداند."""
    if not FILES.exists():
        return []
    unknown: list[Path] = []
    for f in sorted(FILES.rglob("*")):
        if not f.is_file() or f.suffix.lower() not in GAME_EXTS:
            continue
        scored = sorted(
            ((match_score(f.stem, g.title), g) for g in games), key=lambda x: -x[0]
        )
        best_score, best = scored[0]
        if best_score >= 0.6:
            best.files.append(f)
            continue
        if ask and sys.stdin.isatty():
            print(f"\nفایل ناشناخته: {f.name}  (حجم {f.stat().st_size / 2**20:.0f} MB)")
            for i, g in enumerate(games, 1):
                print(f"  {i:2d}) [{g.folder[:4]}] {g.title}")
            print("   0) رد کن")
            try:
                c = int(input("شماره بازی: ").strip() or "0")
            except ValueError:
                c = 0
            if 1 <= c <= len(games):
                games[c - 1].files.append(f)
                continue
        unknown.append(f)
    return unknown


# ---------------------------------------------------------------- planning
def plan(games: list[Game], cap_gb: float, margin_gb: float) -> dict[str, list[list[Game]]]:
    """بازی‌ها را به‌صورت حریصانه در چند آرشیو زیر سقف حجم می‌چیند."""
    cap = int((cap_gb - margin_gb) * 1024**3)
    result: dict[str, list[list[Game]]] = {}
    for folder in ("nostalgia", "graphics"):
        items = [g for g in games if g.folder == folder]
        # بزرگ‌ها اول؛ تا آرشیوها متعادل‌تر پر شوند
        items.sort(key=lambda g: -g.size_bytes)
        bins: list[list[Game]] = []
        for g in items:
            placed = False
            for b in bins:
                if sum(x.size_bytes for x in b) + g.size_bytes <= cap:
                    b.append(g)
                    placed = True
                    break
            if not placed:
                bins.append([g])
        for b in bins:
            b.sort(key=lambda g: g.title.lower())
        result[folder] = bins
    return result


def human(n: int) -> str:
    return f"{n / 1024**3:.2f} GB" if n >= 1024**3 else f"{n / 1024**2:.0f} MB"


def report(games: list[Game], data: dict) -> None:
    print("=" * 66)
    print("گزارش کالکشن PPSSPP")
    print("=" * 66)
    for folder, meta in data["folders"].items():
        items = [g for g in games if g.folder == folder]
        have = [g for g in items if g.files]
        print(f"\n▸ {meta['name_fa']}  ({meta['subtitle_fa']})")
        print(f"  تعداد بازی: {len(items)}   |   موجود در files/: {len(have)}")
        print(f"  حجم تقریبی (ISO): {human(sum(g.size_bytes for g in items))}")
        print(f"  حجم واقعی موجود:  {human(sum(g.size_bytes for g in have))}")
        missing = [g.title for g in items if not g.files]
        if missing:
            print("  در انتظار دانلود:")
            for t in missing:
                print(f"    • {t}")


# ---------------------------------------------------------------- writers
def write_zips(
    plan_: dict[str, list[list[Game]]], data: dict, split: bool
) -> None:
    OUT.mkdir(exist_ok=True)
    for folder, bins in plan_.items():
        meta = data["folders"][folder]
        base = meta["zip_name"]
        for i, b in enumerate(bins, 1):
            if not split:
                name = f"{base}.zip"
            else:
                name = f"{base}_part{i:02d}.zip" if len(bins) > 1 else f"{base}.zip"
            path = OUT / name
            total = sum(g.size_bytes for g in b)
            print(f"\n→ {name}  ({len(b)} بازی، {human(total)})")
            with zipfile.ZipFile(path, "w", zipfile.ZIP_STORED, allowZip64=True) as z:
                for g in b:
                    if not g.files:
                        print(f"   ! {g.title}: فایلش نیست، رد شد")
                        continue
                    for f in g.files:
                        z.write(f, arcname=f"{base}/{f.name}")
                        print(f"   + {f.name}")
            print(f"   ✓ ساخته شد: {path}  ({human(path.stat().st_size)})")


def write_folders(plan_: dict[str, list[list[Game]]], data: dict) -> None:
    """پوشه‌بندی + کپی سخت‌لینک‌شده برای آپلود دستی."""
    import shutil

    OUT.mkdir(exist_ok=True)
    for folder, bins in plan_.items():
        meta = data["folders"][folder]
        for i, b in enumerate(bins, 1):
            name = f"{meta['zip_name']}_part{i:02d}" if len(bins) > 1 else meta["zip_name"]
            d = OUT / name
            d.mkdir(parents=True, exist_ok=True)
            for g in b:
                for f in g.files:
                    target = d / f.name
                    if target.exists():
                        continue
                    try:
                        target.hardlink_to(f)
                    except OSError:
                        shutil.copy2(f, target)
            print(f"✓ پوشه ساخته شد: {d}")


def write_manifests(plan_: dict[str, list[list[Game]]], data: dict) -> None:
    """برای هر آرشیو یک فایل متنی با محتوای دقیق می‌سازد."""
    OUT.mkdir(exist_ok=True)
    for folder, bins in plan_.items():
        meta = data["folders"][folder]
        for i, b in enumerate(bins, 1):
            name = f"{meta['zip_name']}_part{i:02d}" if len(bins) > 1 else meta["zip_name"]
            lines = [
                f"{meta['name_fa']} — {meta['subtitle_fa']}",
                f"آرشیو: {name}.zip",
                "",
            ]
            total = 0
            for n, g in enumerate(b, 1):
                have = "✓" if g.files else "✗"
                lines.append(f"{n:2d}. {have} {g.title} — {human(g.size_bytes)}")
                total += g.size_bytes
            lines += ["", f"مجموع: {human(total)}", "", "اجرا: PPSSPP (Android/Windows/iOS)"]
            (OUT / f"{name}.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")


# ---------------------------------------------------------------- main
def main() -> None:
    ap = argparse.ArgumentParser(description="ساخت ZIP های کالکشن PPSSPP")
    ap.add_argument("--report", action="store_true", help="فقط گزارش بده")
    ap.add_argument("--split", action="store_true", help="به چند ZIP زیر سقف حجم تقسیم کن")
    ap.add_argument("--folders-only", action="store_true", help="فقط پوشه بساز، ZIP نه")
    ap.add_argument("--cap-gb", type=float, default=10.0, help="سقف حجم هر آرشیو")
    ap.add_argument("--margin-gb", type=float, default=0.5, help="حاشیه امن زیر سقف")
    ap.add_argument("--no-ask", action="store_true", help="برای فایل ناشناخته نپرس")
    args = ap.parse_args()

    data = load_data()
    games = build_games(data)
    unknown = scan_files(games, ask=not args.no_ask)

    report(games, data)

    if unknown:
        print("\n⚠ این فایل‌ها به هیچ بازی‌ای نسبت داده نشدند:")
        for u in unknown:
            print(f"   - {u.name}")
        print("  اسم‌شان را دستی در data/games.json اضافه کنید یا با --no-ask رد شوید.")

    if args.report:
        return

    plan_ = plan(games, args.cap_gb, args.margin_gb)
    write_manifests(plan_, data)
    if args.folders_only:
        write_folders(plan_, data)
    else:
        write_zips(plan_, data, split=args.split)
    print("\nتمام. خروجی در پوشه‌ی out/")


if __name__ == "__main__":
    main()
