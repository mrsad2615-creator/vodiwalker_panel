#!/usr/bin/env python3
"""
render.py — از data/games.json سه خروجی می‌سازد:

  PSP_01_Nostalgia.md   لیست متنی پوشه‌ی نوستالژی
  PSP_02_Graphics.md    لیست متنی پوشه‌ی گرافیک بالا
  index.html            کاتالوگ تصویری (فقط با پایتون، بدون وابستگی)

اجرا:  python3 render.py
"""

from __future__ import annotations

import html
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATA = json.loads((ROOT / "data" / "games.json").read_text(encoding="utf-8"))
LIMIT_GB = DATA["meta"]["limit_gb"]
MARGIN = DATA["meta"]["safety_margin_gb"]


def human(mb: float) -> str:
    return f"{mb / 1024:.2f} GB" if mb >= 1024 else f"{mb:.0f} MB"


def folder_games(fid: str) -> list[dict]:
    return [g for g in DATA["games"] if g["folder"] == fid]


def totals(fid: str) -> tuple[float, float]:
    g = folder_games(fid)
    return sum(x["iso_mb"] for x in g) / 1024, sum(x["cso_mb"] for x in g) / 1024


# ----------------------------------------------------------------- markdown
def render_md(fid: str) -> str:
    meta = DATA["folders"][fid]
    games = folder_games(fid)
    iso, cso = totals(fid)
    out: list[str] = [
        f"# {meta['name_fa']}",
        f"### {meta['subtitle_fa']}",
        "",
        meta["desc_fa"],
        "",
        f"- تعداد بازی: **{len(games)}**",
        f"- حجم با ISO کامل: **{human(iso * 1024)}**",
        f"- حجم با CSO فشرده: **{human(cso * 1024)}**  ← پیشنهادی",
        f"- سقف هر آرشیو: **{LIMIT_GB} GB** (حاشیه امن {MARGIN} GB)",
        "",
        "> همه‌ی این بازی‌ها با آخرین نسخه‌ی PPSSPP (Android/Windows/macOS/Linux/iOS) اجرا می‌شوند. "
        "فرمت `.cso` و `.iso` و `.chd` هر سه پشتیبانی می‌شوند.",
        "",
        "---",
        "",
        "| # | بازی | سال | سبک | ISO | CSO |",
        "|---|------|-----|-----|-----|-----|",
    ]
    for i, g in enumerate(games, 1):
        out.append(
            f"| {i} | **{g['title']}** — {g['title_fa']} | {g['year']} | "
            f"{g['genre_fa']} | {g['iso_mb']} MB | {g['cso_mb']} MB |"
        )

    out += ["", "---", "", "## معرفی بازی‌ها", ""]
    for i, g in enumerate(games, 1):
        out += [
            f"### {i}. {g['title']} ({g['year']})",
            f"*{g['title_fa']} — {g['genre_fa']} — {g['dev']} — نمره‌ی متاکریتیک: {g['score']}*",
            "",
            g["block_fa"],
            "",
            f"**نکته‌ی PPSSPP:** {g['tip_fa']}",
            "",
            f"**حجم:** ISO {g['iso_mb']} MB · CSO {g['cso_mb']} MB",
            "",
            "**لینک‌های دانلود:** "
            + " · ".join(f"[{s['name']}]({s['url']})" for s in g["sources"]),
            "",
        ]

    alts = DATA.get("alternates", {}).get(fid, [])
    if alts:
        out += ["---", "", "## جایگزین‌ها (اگر یکی را نخواستید)", ""]
        out += ["| بازی | سال | ISO | CSO | چرا؟ |", "|------|-----|-----|-----|------|"]
        for a in alts:
            out.append(
                f"| **{a['title']}** — {a['title_fa']} | {a['year']} | {a['iso_mb']} MB | "
                f"{a['cso_mb']} MB | {a['why_fa']} |"
            )
        out += ["", ""]

    out += [
        "---",
        "",
        "## ساخت آرشیو ZIP",
        "",
        "1. همه‌ی فایل‌ها را داخل پوشه‌ی `files/` بگذارید (اسم فایل مهم نیست، اسکریپت خودش تشخیص می‌دهد).",
        "2. `python3 build_zips.py --report` بزنید تا ببینید چه چیزی کم است.",
        "3. `python3 build_zips.py --split` بزنید تا آرشیوهای زیر ۱۰ گیگ ساخته شوند.",
        "",
        "خروجی در `out/` می‌آید: فایل‌های ZIP + یک `manifest.txt` برای هر آرشیو.",
        "",
    ]
    return "\n".join(out)


# ----------------------------------------------------------------- html
CARD = """
<article class="card" data-genre="{genre}" data-year="{year}" data-search="{search}">
  <div class="card-top">
    <span class="idx">{idx}</span>
    <span class="year">{year}</span>
  </div>
  <h3>{title}</h3>
  <div class="sub">{title_fa}</div>
  <div class="tags"><span>{genre_fa}</span><span>{dev}</span><span class="score">★ {score}</span></div>
  <p>{block}</p>
  <p class="tip"><b>PPSSPP:</b> {tip}</p>
  <div class="sizes"><span>ISO {iso}</span><span>CSO {cso}</span></div>
  <div class="links">{links}</div>
</article>
"""


def render_html() -> str:
    nav = []
    sections = []
    for fid, meta in DATA["folders"].items():
        games = folder_games(fid)
        iso, cso = totals(fid)
        nav.append(
            f'<a href="#{fid}" class="navlink">{meta["name_fa"]} '
            f'<b>{len(games)}</b></a>'
        )
        cards = []
        for i, g in enumerate(games, 1):
            links = " · ".join(
                f'<a href="{html.escape(s["url"])}" target="_blank" rel="noopener">'
                f'{html.escape(s["name"])}</a>'
                for s in g["sources"]
            )
            cards.append(
                CARD.format(
                    genre=g["genre"],
                    year=g["year"],
                    search=html.escape((g["title"] + " " + g["title_fa"]).lower()),
                    idx=i,
                    title=html.escape(g["title"]),
                    title_fa=html.escape(g["title_fa"]),
                    genre_fa=html.escape(g["genre_fa"]),
                    dev=html.escape(g["dev"]),
                    score=g["score"],
                    block=html.escape(g["block_fa"]),
                    tip=html.escape(g["tip_fa"]),
                    iso=human(g["iso_mb"]),
                    cso=human(g["cso_mb"]),
                    links=links,
                )
            )
        alt_html = ""
        alts = DATA.get("alternates", {}).get(fid, [])
        if alts:
            rows = "".join(
                f"<tr><td><b>{html.escape(a['title'])}</b><br><span class=muted>"
                f"{html.escape(a['title_fa'])}</span></td><td>{a['year']}</td>"
                f"<td>{human(a['iso_mb'])}</td><td>{html.escape(a['why_fa'])}</td></tr>"
                for a in alts
            )
            alt_html = f"""
<details class="alts">
  <summary>جایگزین‌ها — اگر یکی از این‌ها را بیشتر دوست داشتید ({len(alts)} بازی)</summary>
  <table><thead><tr><th>بازی</th><th>سال</th><th>ISO</th><th>چرا؟</th></tr></thead>
  <tbody>{rows}</tbody></table>
</details>"""

        sections.append(
            f"""
<section id="{fid}" style="--accent:{meta['color']}">
  <header class="sec-head">
    <div>
      <h2>{meta['name_fa']}</h2>
      <p class="muted">{meta['subtitle_fa']}</p>
    </div>
    <div class="stats">
      <div><b>{len(games)}</b><span>بازی</span></div>
      <div><b>{human(iso * 1024)}</b><span>ISO</span></div>
      <div><b>{human(cso * 1024)}</b><span>CSO</span></div>
    </div>
  </header>
  <p class="desc">{meta['desc_fa']}</p>
  <div class="grid">{''.join(cards)}</div>
  {alt_html}
</section>"""
        )

    return f"""<!doctype html>
<html lang="fa" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>کالکشن PPSSPP — {len(DATA['games'])} بازی در دو پوشه</title>
<style>
  :root {{
    --bg:#0b1020; --panel:#121a33; --panel2:#1a2547; --line:#25325c;
    --text:#e9eefc; --muted:#93a0c4; --accent:#4f8cff;
  }}
  * {{ box-sizing:border-box; }}
  body {{
    margin:0; background:radial-gradient(1200px 600px at 80% -10%, #1b2a55 0%, var(--bg) 55%);
    color:var(--text); font-family:Vazirmatn, "Segoe UI", Tahoma, sans-serif;
    line-height:1.75; padding:28px 18px 80px;
  }}
  .wrap {{ max-width:1180px; margin:0 auto; }}
  h1 {{ font-size:clamp(24px,3.4vw,40px); margin:0 0 6px; letter-spacing:-.5px; }}
  .lede {{ color:var(--muted); margin:0 0 22px; }}
  .hero {{
    background:linear-gradient(135deg, #16224a, #0e1730);
    border:1px solid var(--line); border-radius:20px; padding:26px 26px 22px;
    margin-bottom:26px; position:relative; overflow:hidden;
  }}
  .hero:before {{
    content:""; position:absolute; inset:auto -60px -120px auto; width:320px; height:320px;
    background:radial-gradient(circle, rgba(79,140,255,.35), transparent 70%);
  }}
  .kpis {{ display:flex; flex-wrap:wrap; gap:12px; margin-top:18px; }}
  .kpi {{
    background:rgba(255,255,255,.04); border:1px solid var(--line); border-radius:14px;
    padding:10px 16px; min-width:132px;
  }}
  .kpi b {{ display:block; font-size:20px; }}
  .kpi span {{ color:var(--muted); font-size:12px; }}
  nav {{ display:flex; gap:10px; flex-wrap:wrap; margin-bottom:26px; }}
  .navlink {{
    text-decoration:none; color:var(--text); background:var(--panel);
    border:1px solid var(--line); padding:9px 15px; border-radius:999px; font-size:14px;
  }}
  .navlink b {{ color:var(--accent); }}
  .tools {{ display:flex; gap:10px; flex-wrap:wrap; margin:0 0 26px; }}
  .tools input, .tools select {{
    background:var(--panel); color:var(--text); border:1px solid var(--line);
    border-radius:12px; padding:11px 14px; font-family:inherit; font-size:14px;
  }}
  .tools input {{ flex:1 1 260px; }}
  section {{ margin-bottom:54px; }}
  .sec-head {{ display:flex; justify-content:space-between; align-items:flex-end; gap:18px; flex-wrap:wrap; }}
  .sec-head h2 {{ margin:0; font-size:24px; border-right:4px solid var(--accent); padding-right:12px; }}
  .stats {{ display:flex; gap:20px; }}
  .stats div {{ text-align:center; }}
  .stats b {{ display:block; font-size:17px; color:var(--accent); }}
  .stats span {{ font-size:11px; color:var(--muted); }}
  .desc {{ color:var(--muted); max-width:80ch; margin:12px 0 22px; }}
  .grid {{ display:grid; grid-template-columns:repeat(auto-fill, minmax(290px,1fr)); gap:16px; }}
  .card {{
    background:linear-gradient(180deg, var(--panel) 0%, #0f1730 100%);
    border:1px solid var(--line); border-radius:18px; padding:18px;
    display:flex; flex-direction:column; transition:.18s;
  }}
  .card:hover {{ transform:translateY(-3px); border-color:var(--accent); }}
  .card-top {{ display:flex; justify-content:space-between; align-items:center; font-size:12px; color:var(--muted); }}
  .idx {{ background:var(--accent); color:#04102b; font-weight:800; border-radius:8px; padding:1px 9px; }}
  .card h3 {{ margin:10px 0 0; font-size:17px; line-height:1.4; }}
  .sub {{ color:var(--muted); font-size:13px; margin-bottom:8px; }}
  .tags {{ display:flex; flex-wrap:wrap; gap:6px; margin-bottom:10px; }}
  .tags span {{
    font-size:11px; background:var(--panel2); border:1px solid var(--line);
    border-radius:999px; padding:2px 9px; color:#c8d3f0;
  }}
  .tags .score {{ color:#ffcf6b; border-color:#4a3d1e; background:#241d0d; }}
  .card p {{ font-size:13.5px; margin:0 0 10px; color:#dfe6fa; }}
  .card p.tip {{ color:var(--muted); font-size:12.5px; background:rgba(79,140,255,.07);
    border-radius:10px; padding:8px 10px; }}
  .sizes {{ display:flex; gap:10px; font-size:12px; color:var(--muted); margin-top:auto; }}
  .sizes span {{ border:1px dashed var(--line); border-radius:8px; padding:2px 8px; }}
  .links {{ margin-top:10px; font-size:12.5px; }}
  .links a {{ color:var(--accent); text-decoration:none; }}
  .links a:hover {{ text-decoration:underline; }}
  .alts {{ margin-top:20px; background:var(--panel); border:1px solid var(--line); border-radius:14px; padding:14px 18px; }}
  .alts summary {{ cursor:pointer; font-weight:700; }}
  table {{ width:100%; border-collapse:collapse; margin-top:12px; font-size:13px; }}
  th, td {{ text-align:right; padding:8px 10px; border-bottom:1px solid var(--line); vertical-align:top; }}
  th {{ color:var(--muted); font-weight:600; }}
  .muted {{ color:var(--muted); }}
  footer {{ color:var(--muted); font-size:12.5px; border-top:1px solid var(--line); padding-top:18px; }}
  .hidden {{ display:none !important; }}
  .empty {{ color:var(--muted); padding:30px; text-align:center; }}
</style>
</head>
<body>
<div class="wrap">
  <div class="hero">
    <h1>کالکشن PPSSPP</h1>
    <p class="lede">{len(DATA['games'])} بازی منتخب، دو پوشه، هر آرشیو زیر {LIMIT_GB} گیگابایت — آماده‌ی دانلود و اجرا روی PPSSPP.</p>
    <div class="kpis">
      <div class="kpi"><b>{len(folder_games('nostalgia'))}</b><span>بازی نوستالژی</span></div>
      <div class="kpi"><b>{len(folder_games('graphics'))}</b><span>بازی گرافیک بالا</span></div>
      <div class="kpi"><b>{human(totals('nostalgia')[0] * 1024)}</b><span>حجم پوشه ۱ (ISO)</span></div>
      <div class="kpi"><b>{human(totals('graphics')[1] * 1024)}</b><span>حجم پوشه ۲ (CSO)</span></div>
      <div class="kpi"><b>{LIMIT_GB} GB</b><span>سقف هر آرشیو</span></div>
    </div>
  </div>

  <nav>{''.join(nav)}</nav>

  <div class="tools">
    <input id="q" type="search" placeholder="جست‌وجو در نام بازی…">
    <select id="g">
      <option value="">همه‌ی سبک‌ها</option>
      <option value="Action">اکشن</option>
      <option value="Racing">مسابقه‌ای</option>
      <option value="RPG">نقش‌آفرینی</option>
      <option value="Fighting">مبارزه‌ای</option>
      <option value="Platform">پلتفرمر</option>
      <option value="Puzzle">پازل</option>
      <option value="Sports">ورزشی</option>
    </select>
    <select id="s">
      <option value="">مرتب‌سازی: پیشنهادی</option>
      <option value="year">سال انتشار</option>
      <option value="score">نمره</option>
    </select>
  </div>

  {''.join(sections)}

  <footer>
    حجم‌ها تقریبی‌اند (±۱۰٪) و بر پایه‌ی دامپ‌های Redump و میانگین CSO محاسبه شده‌اند.
    اسکریپت <code>build_zips.py</code> حجم واقعی فایل‌های شما را می‌سنجد و بر همان اساس آرشیو می‌سازد.
    لینک‌ها به آرشیوهای عمومی PSP اشاره می‌کنند؛ فایل بازی را فقط در صورت داشتن نسخه‌ی قانونی نگه دارید.
  </footer>
</div>

<script>
const cards = [...document.querySelectorAll('.card')];
const q = document.getElementById('q');
const g = document.getElementById('g');
const s = document.getElementById('s');

function apply() {{
  const term = q.value.trim().toLowerCase();
  const genre = g.value;
  cards.forEach(c => {{
    const okTerm = !term || c.dataset.search.includes(term);
    const okGenre = !genre || c.dataset.genre.includes(genre);
    c.classList.toggle('hidden', !(okTerm && okGenre));
  }});
  if (s.value) {{
    const key = s.value === 'year' ? 'year' : 'score';
    cards.sort((a, b) => {{
      const av = key === 'year' ? +a.dataset.year : 0;
      const bv = key === 'year' ? +b.dataset.year : 0;
      return key === 'year' ? bv - av : 0;
    }});
    cards.forEach(c => c.parentElement.appendChild(c));
  }}
}}
q.addEventListener('input', apply);
g.addEventListener('change', apply);
s.addEventListener('change', apply);
</script>
</body>
</html>
"""


if __name__ == "__main__":
    for fid in DATA["folders"]:
        name = DATA["folders"][fid]["zip_name"] + ".md"
        (ROOT / name).write_text(render_md(fid), encoding="utf-8")
        print("✓", name)
    (ROOT / "index.html").write_text(render_html(), encoding="utf-8")
    print("✓ index.html")
