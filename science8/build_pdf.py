#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Build premium Persian PDF book: Science 8 Exam Guide Ch1-4 (ReportLab, RTL)."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import arabic_reshaper
from bidi.algorithm import get_display
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.colors import HexColor, white, black
from reportlab.lib.enums import TA_RIGHT, TA_CENTER, TA_JUSTIFY
from reportlab.platypus import (SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, Flowable,
                                Image, PageBreak, KeepTogether, HRFlowable, ListFlowable, ListItem)
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib import colors

BASE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(BASE, "fonts")
ASSETS = os.path.join(BASE, "assets")
OUT = os.path.join(BASE, "output")
os.makedirs(OUT, exist_ok=True)

pdfmetrics.registerFont(TTFont("Vazir", os.path.join(FONTS, "Vazirmatn-Regular.ttf")))
pdfmetrics.registerFont(TTFont("Vazir-Bold", os.path.join(FONTS, "Vazirmatn-Bold.ttf")))
pdfmetrics.registerFont(TTFont("Vazir-Med", os.path.join(FONTS, "Vazirmatn-Medium.ttf")))
pdfmetrics.registerFontFamily("Vazir", normal="Vazir", bold="Vazir-Bold", italic="Vazir", boldItalic="Vazir-Bold")

import re as _re
_FA_DIGITS = str.maketrans("0123456789", "۰۱۲۳۴۵۶۷۸۹")

def to_fa_digits(t):
    """Convert ASCII digits to Persian digits, but keep technical tokens (C-12, Z=6, A=14) in Latin form."""
    t = str(t)
    def repl(m):
        return m.group(0).translate(_FA_DIGITS).replace(".", "٫")
    # a digit run that is not glued to a Latin letter, '=' or '-' on its left
    return _re.sub(r"(?<![A-Za-z0-9=\-–_/.])\d+(?:\.\d+)?(?![A-Za-z])", repl, t)

def fa(t):
    if t is None:
        return ""
    return get_display(arabic_reshaper.reshape(to_fa_digits(t)))

W, H = A4
MARGIN = 42

# ---------- styles ----------
def S(name, **kw):
    base = dict(fontName="Vazir", fontSize=11, leading=20, alignment=TA_RIGHT,
                textColor=HexColor("#1F2937"), spaceAfter=6, spaceBefore=2)
    base.update(kw)
    return ParagraphStyle(name, **base)

stBody = S("Body")
stBodyJ = S("BodyJ", alignment=TA_JUSTIFY)
stH1 = S("H1", fontName="Vazir-Bold", fontSize=20, leading=28, textColor=HexColor("#0F2A43"), spaceBefore=14, spaceAfter=10)
stH2 = S("H2", fontName="Vazir-Bold", fontSize=15, leading=24, textColor=HexColor("#0F2A43"), spaceBefore=12, spaceAfter=8)
stH3 = S("H3", fontName="Vazir-Bold", fontSize=12.5, leading=21, textColor=HexColor("#1F2937"), spaceBefore=10, spaceAfter=6)
stCap = S("Cap", fontSize=9, leading=15, alignment=TA_CENTER, textColor=HexColor("#6B7280"))
stQ = S("Q", fontSize=11, leading=20)
stAns = S("Ans", fontSize=10.5, leading=19)
stSmall = S("Small", fontSize=9.5, leading=17, textColor=HexColor("#4B5563"))
stTitle = S("Title", fontName="Vazir-Bold", fontSize=26, leading=36, alignment=TA_CENTER, textColor=HexColor("#0F2A43"))
stSub = S("Sub", fontSize=13, leading=22, alignment=TA_CENTER, textColor=HexColor("#374151"))

def P(text, style=stBody):
    return Paragraph(fa(text), style)

def img(name, width_mm=170):
    path = os.path.join(ASSETS, name)
    if not os.path.exists(path):
        return Spacer(1, 4)
    from PIL import Image as PILImage
    iw, ih = PILImage.open(path).size
    w = width_mm * mm
    h = w * ih / iw
    return Image(path, width=w, height=h)

PAGEMAP = {}
PREV_MAP = {}

class Bookmark(Flowable):
    """Invisible flowable: registers a named destination, a PDF outline entry and records its page number."""
    def __init__(self, key, title, level=0):
        Flowable.__init__(self)
        self.key = key
        self.title = title
        self.level = level

    def wrap(self, aw, ah):
        return (0, 0)

    def draw(self):
        canv = self.canv
        canv.bookmarkPage(self.key, fit="XYZ", left=0, top=H, zoom=0)
        try:
            canv.addOutlineEntry(self.title, self.key, level=self.level, closed=False)
        except Exception:
            pass
        PAGEMAP[self.key] = canv.getPageNumber()

def bm(key, title=None, level=1):
    return Bookmark(key, title or key, level=level)

def toc_table(entries):
    """entries: list of (key, label, level). Renders title | page with internal links."""
    head = [Paragraph(fa("صفحه"), S("th1", fontName="Vazir-Bold", fontSize=10, alignment=TA_CENTER, textColor=white)),
            Paragraph(fa("عنوان بخش"), S("th2", fontName="Vazir-Bold", fontSize=10, alignment=TA_RIGHT, textColor=white))]
    data = [head]
    for key, label, level in entries:
        pg = PAGEMAP.get(key) or PREV_MAP.get(key)
        pg_txt = fa(str(pg)) if pg else "—"
        prefix = ""
        style = S("tocl", fontSize=10.5 if level == 0 else 10, leading=17,
                  fontName="Vazir-Bold" if level == 0 else "Vazir",
                  alignment=TA_RIGHT, rightIndent=0 if level == 0 else 10,
                  textColor=HexColor("#0F2A43" if level == 0 else "#374151"))
        link = f'<a href="#{key}" color="#{"0F2A43" if level == 0 else "374151"}">{fa(prefix + label)}</a>'
        data.append([Paragraph(fa(pg_txt), S("tocp", fontSize=10, alignment=TA_CENTER)),
                     Paragraph(link, style)])
    t = Table(data, colWidths=[20 * mm, (W - 2 * MARGIN) - 20 * mm], repeatRows=1)
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), HexColor("#0F2A43")),
        ("GRID", (0, 0), (-1, -1), 0.4, HexColor("#E5E7EB")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 6), ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 4), ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [white, HexColor("#F9FAFB")]),
    ]))
    return t

def panel(title, body, bg="#F3F4F6", border="#0F2A43", title_color="#0F2A43"):
    """Bordered panel. body may be a string or a list of strings (one row each, keeps bidi order)."""
    items = [body] if isinstance(body, str) else list(body)
    inner = [[Paragraph(fa(title), S("pt", fontName="Vazir-Bold", fontSize=11, leading=18,
                                    textColor=HexColor(title_color)))]]
    for item in items:
        inner.append([Paragraph(fa(item), S("pb", fontSize=10.5, leading=19))])
    t = Table(inner, colWidths=[W - 2 * MARGIN - 12])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), HexColor(bg)),
        ("BOX", (0, 0), (-1, -1), 1, HexColor(border)),
        ("LINEBELOW", (0, 0), (-1, 0), 0.6, HexColor(border)),
        ("LEFTPADDING", (0, 0), (-1, -1), 10), ("RIGHTPADDING", (0, 0), (-1, -1), 10),
        ("TOPPADDING", (0, 0), (-1, -1), 5), ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("ROUNDEDCORNERS", [4, 4, 4, 4]),
    ]))
    return t

def tipbox(text, bg="#FFFBEB", border="#D97706"):
    return panel("نکته امتحانی", text, bg=bg, border=border, title_color="#92400E")

def defbox(term, definition, color="#0E7C7B", bg="#E0F2F2"):
    return panel(f"تعریف: {term}", definition, bg=bg, border=color, title_color=color)

def styled_table(headers, rows, col_widths=None, header_bg="#0F2A43"):
    # RTL: reverse columns so first header appears on right
    h = [Paragraph(fa(x), S("th", fontName="Vazir-Bold", fontSize=10, leading=16, alignment=TA_CENTER, textColor=white)) for x in reversed(headers)]
    data = [h]
    for r in rows:
        data.append([Paragraph(fa(str(c)), S("td", fontSize=10, leading=17, alignment=TA_CENTER)) for c in reversed(r)])
    if col_widths:
        cw = [w*mm for w in reversed(col_widths)]
    else:
        cw = [(W - 2*MARGIN) / len(headers)] * len(headers)
    t = Table(data, colWidths=cw, repeatRows=1)
    style = [
        ("BACKGROUND", (0, 0), (-1, 0), HexColor(header_bg)),
        ("TEXTCOLOR", (0, 0), (-1, 0), white),
        ("GRID", (0, 0), (-1, -1), 0.5, HexColor("#D1D5DB")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 6), ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 5), ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [white, HexColor("#F9FAFB")]),
    ]
    t.setStyle(TableStyle(style))
    return t

def chapter_banner(num, title, subtitle, color, pages, time):
    data = [[
        Paragraph(fa(f"فصل {num}"), S("cn", fontName="Vazir-Bold", fontSize=14, leading=20, alignment=TA_CENTER, textColor=white)),
        Paragraph(fa(title), S("ct", fontName="Vazir-Bold", fontSize=22, leading=30, alignment=TA_CENTER, textColor=white)),
    ], [
        Paragraph(fa(subtitle), S("cs", fontSize=11, leading=18, alignment=TA_CENTER, textColor=white)),
        Paragraph(fa(f"{pages}  |  {time}"), S("cm", fontSize=9.5, leading=16, alignment=TA_CENTER, textColor=white)),
    ]]
    t = Table(data, colWidths=[(W-2*MARGIN)*0.3, (W-2*MARGIN)*0.7])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), HexColor(color)),
        ("SPAN", (0, 0), (0, 1)),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 10), ("RIGHTPADDING", (0, 0), (-1, -1), 10),
        ("TOPPADDING", (0, 0), (-1, -1), 8), ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
        ("ROUNDEDCORNERS", [6, 6, 6, 6]),
    ]))
    return t

def header_footer(canvas, doc):
    canvas.saveState()
    if doc.page == 1:
        canvas.restoreState()
        return
    canvas.setFont("Vazir", 8)
    canvas.setFillColor(HexColor("#6B7280"))
    canvas.drawCentredString(W/2, H - 28, fa("علوم هشتم | آمادگی جامع امتحان | فصل‌های ۱ تا ۴ | ۱۴۰۵–۱۴۰۶"))
    canvas.setStrokeColor(HexColor("#E5E7EB"))
    canvas.line(MARGIN, H - 34, W - MARGIN, H - 34)
    canvas.setFont("Vazir", 9)
    canvas.drawCentredString(W/2, 26, fa(f"صفحه {doc.page}"))
    canvas.restoreState()

def build(filename, story, title="علوم هشتم | آمادگی جامع امتحان"):
    doc = SimpleDocTemplate(filename, pagesize=A4, leftMargin=MARGIN, rightMargin=MARGIN,
                            topMargin=48, bottomMargin=40, title=title, author="Science 8 Exam Guide")
    doc.build(story, onFirstPage=header_footer, onLaterPages=header_footer)
    print("built", filename)

# ---------- content builders ----------
from data_ch1 import CHAPTER as C1, QUESTIONS as Q1
from data_ch2 import CHAPTER as C2, QUESTIONS as Q2
from data_ch3 import CHAPTER as C3, QUESTIONS as Q3
from data_ch4 import CHAPTER as C4, QUESTIONS as Q4
from data_exams import EXAM1, EXAM2, EXAM3, GLOSSARY, COMPARISONS, MISTAKES, MEMORY, CHECKLIST

CHAPTERS = [C1, C2, C3, C4]
QMAP = {1: Q1, 2: Q2, 3: Q3, 4: Q4}
FIGMAP = {
    1: [("ch1_classification.png", "شکل ۱-۱: طبقه‌بندی مخلوط‌ها"),
        ("ch1_solution.png", "شکل ۱-۲: اجزای محلول"),
        ("ch1_separation.png", "شکل ۱-۳: انتخاب روش جداسازی"),
        ("ch1_distillation.png", "شکل ۱-۴: دستگاه تقطیر ساده"),
        ("concept_ch1.png", "شکل ۱-۵: نقشه مفهومی فصل ۱")],
    2: [("ch2_change_compare.png", "شکل ۲-۱: تغییر فیزیکی در برابر شیمیایی"),
        ("ch2_fire_triangle.png", "شکل ۲-۲: مثلث آتش"),
        ("concept_ch2.png", "شکل ۲-۳: نقشه مفهومی فصل ۲")],
    3: [("ch3_atom_model.png", "شکل ۳-۱: مدل اتم کربن"),
        ("ch3_numbers.png", "شکل ۳-۲: عدد اتمی و عدد جرمی"),
        ("ch3_isotopes.png", "شکل ۳-۳: ایزوتوپ‌های کربن"),
        ("concept_ch3.png", "شکل ۳-۴: نقشه مفهومی فصل ۳")],
    4: [("ch4_system.png", "شکل ۴-۱: دستگاه عصبی مرکزی و محیطی"),
        ("ch4_brain.png", "شکل ۴-۲: مغز و نخاع"),
        ("ch4_neuron.png", "شکل ۴-۳: نورون و مسیر پیام"),
        ("ch4_reflex.png", "شکل ۴-۴: کمان انعکاس"),
        ("concept_ch4.png", "شکل ۴-۵: نقشه مفهومی فصل ۴")],
}

LEVEL_FA = {1: "سطح ۱: آسان", 2: "سطح ۲: متوسط", 3: "سطح ۳: سخت", 4: "سطح ۴: پیشرفته"}
TAG_FA = {"HIGH PRIORITY": "پراولویت بالا", "FREQUENTLY ASKED": "پرتکرار", "CONCEPTUAL": "مفهومی",
          "CHALLENGE": "چالش", "EXAM TRAP": "دام امتحانی"}

def q_block(idx, q):
    parts = []
    badge = f"{q['type']}  |  {LEVEL_FA.get(q['level'],'')}  |  {TAG_FA.get(q['tag'], q['tag'])} ({q['tag']})"
    parts.append(Paragraph(fa(badge), S("badge", fontSize=8.5, leading=14, textColor=HexColor("#6B7280"))))
    parts.append(Paragraph(fa(f"سؤال {idx}: {q['q']}"), stQ))
    if q.get("options"):
        # four options in a compact 2x2 grid (RTL: first option top-right)
        letters = ["الف", "ب", "ج", "د"]
        opts = q["options"]
        def opt_cell(i):
            return Paragraph(fa(f"{letters[i]}) {opts[i]}"), S("opt", fontSize=10.5, leading=18))
        cells = [[opt_cell(1), opt_cell(0)], [opt_cell(3), opt_cell(2)]]
        half = (W - 2 * MARGIN) / 2 - 12
        t = Table(cells, colWidths=[half, half])
        t.setStyle(TableStyle([
            ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
            ("LEFTPADDING", (0, 0), (-1, -1), 2), ("RIGHTPADDING", (0, 0), (-1, -1), 14),
            ("TOPPADDING", (0, 0), (-1, -1), 1), ("BOTTOMPADDING", (0, 0), (-1, -1), 1),
        ]))
        parts.append(t)
        parts.append(Spacer(1, 4))
    return parts

def a_block(idx, q):
    parts = []
    parts.append(Paragraph(fa(f"پاسخ سؤال {idx}"), S("ah", fontName="Vazir-Bold", fontSize=11, leading=18, textColor=HexColor("#065F46"))))
    parts.append(Paragraph(fa(f"پاسخ درست: {q['answer']}"), stAns))
    parts.append(Paragraph(fa(f"توضیح: {q['explain']}"), stAns))
    if q.get("tip"):
        parts.append(Paragraph(fa(f"نکته امتحانی: {q['tip']}"), S("at", fontSize=10, leading=18, textColor=HexColor("#92400E"))))
    if q.get("keywords"):
        parts.append(Paragraph(fa(f"کلیدواژه‌های پاسخ: {q['keywords']}"), S("ak", fontSize=9.5, leading=17, textColor=HexColor("#374151"))))
    if q["type"] in ("تشریحی", "کوتاه‌پاسخ") and q.get("keywords"):
        parts.append(Paragraph(fa("پاسخ کوتاه برای جلسه امتحان: " + q["answer"]), S("short", fontSize=10, leading=18, textColor=HexColor("#1F2937"))))
    return parts

def chapter_teaching(ch):
    s = []
    s.append(chapter_banner(ch["num"], ch["title"], ch["subtitle"], ch["color"], ch["textbook_pages"], ch["study_time"]))
    s.append(Spacer(1, 8))
    s.append(P("مقدمه فصل", stH2))
    s.append(P(ch["intro"], stBodyJ))
    s.append(P("اهداف یادگیری", stH2))
    for o in ch["objectives"]:
        s.append(P(f"• {o}"))
    s.append(P("آموزش کامل مفاهیم", stH1))
    for c in ch["concepts"]:
        s.append(P(c["title"], stH2))
        s.append(defbox(c["title"], c["what"], color=ch["color"], bg=ch["color_light"]))
        s.append(Spacer(1, 4))
        s.append(P(f"چرا مهم است؟ {c['why']}"))
        s.append(P(f"چگونه کار می‌کند؟ {c['how']}"))
        s.append(P(f"مثال زندگی واقعی: {c['example']}"))
        s.append(tipbox(f"ارتباط با امتحان: {c['exam']}"))
        s.append(Spacer(1, 2))
        s.append(panel("اشتباه رایج", c["trap"], bg="#FEF2F2", border="#DC2626", title_color="#991B1B"))
        s.append(Spacer(1, 6))
    s.append(P("نکات کلیدی فصل", stH2))
    for k in ch["key_points"]:
        s.append(P(f"• {k}"))
    s.append(P("تحلیل فعالیت‌ها و آزمایش‌های کتاب درسی", stH1))
    for a in ch["activities"]:
        s.append(P(a["title"], stH2))
        s.append(P(f"هدف: {a['objective']}"))
        s.append(P(f"وسایل: {a['materials']}"))
        s.append(P(f"روش کار: {a['procedure']}"))
        s.append(P(f"مشاهده: {a['observation']}"))
        s.append(P(f"توضیح علمی: {a['explanation']}"))
        s.append(P(f"نتیجه‌گیری: {a['conclusion']}"))
        s.append(tipbox(f"سؤال احتمالی امتحان: {a['exam_q']}"))
        s.append(panel("اشتباه رایج", a["mistakes"], bg="#FEF2F2", border="#DC2626", title_color="#991B1B"))
        s.append(Spacer(1, 6))
    s.append(P("تحلیل شکل‌ها و نمودارهای کتاب درسی", stH1))
    for f in ch["figures"]:
        s.append(P(f["title"], stH2))
        s.append(P(f["desc"]))
        s.append(P(f"اجزا: {f['components']}"))
        s.append(P(f"برچسب‌های مهم: {f['labels']}"))
        s.append(P(f"رابطه اجزا: {f['relations']}"))
        s.append(tipbox(f"سؤال احتمالی از شکل: {f['exam_q']}"))
        s.append(Spacer(1, 4))
    return s

def chapter_visuals(ch):
    s = []
    s.append(P(f"خلاصه تصویری فصل {ch['num']}: {ch['title']}", stH1))
    s.append(P("در این بخش، مهم‌ترین مفاهیم فصل به صورت نمودار و اینفوگرافیک آمده است. هر نمودار را با دقت بخوانید و ارتباط اجزای آن را درک کنید."))
    for fname, cap in FIGMAP[ch["num"]]:
        s.append(img(fname))
        s.append(P(cap, stCap))
        s.append(Spacer(1, 6))
    s.append(P("جدول جمع‌بندی فصل", stH2))
    rows = [[f"مفهوم {i+1}", c["title"], c["what"][:90] + "…"] for i, c in enumerate(ch["concepts"])]
    s.append(styled_table(["ردیف", "مفهوم", "خلاصه"], rows, col_widths=[18, 45, 107], header_bg=ch["color"]))
    return s

def chapter_questions(ch, qs):
    s = []
    s.append(P(f"بانک سؤالات فصل {ch['num']}: {ch['title']}", stH1))
    s.append(P(f"این بانک شامل {len(qs)} سؤال در چهار سطح دشواری است. سؤال‌ها را بدون نگاه‌کردن به پاسخ حل کنید و سپس پاسخ‌نامه را بررسی کنید. برچسب‌ها راهنمای اهمیت سؤال‌اند: پراولویت بالا، پرتکرار، مفهومی، چالش و دام امتحانی."))
    s.append(P("سؤالات چهارگزینه‌ای", stH2))
    idx = 1
    for q in qs:
        if q["type"] == "چهارگزینه‌ای":
            for el in q_block(idx, q):
                s.append(el)
            s.append(Spacer(1, 4))
            idx += 1
    for t in ["درست/نادرست", "جای خالی", "کوتاه‌پاسخ", "تشریحی", "تعریف", "چرا", "مقایسه", "تطبیق", "تحلیل شکل", "تحلیل آزمایش", "کاربردی", "چندمفهومی", "دام امتحانی"]:
        group = [q for q in qs if q["type"] == t]
        if not group:
            continue
        s.append(P(f"سؤالات {t}", stH2))
        for q in group:
            for el in q_block(idx, q):
                s.append(el)
            s.append(Spacer(1, 4))
            idx += 1
    return s

def chapter_answers(ch, qs):
    s = []
    s.append(P(f"پاسخ‌نامه تشریحی فصل {ch['num']}: {ch['title']}", stH1))
    s.append(P("در این بخش، پاسخ هر سؤال همراه با توضیح علمی، دلیل درستی و نکته امتحانی آمده است. برای سؤالات تشریحی، پاسخ کامل، پاسخ کوتاه جلسه امتحان و کلیدواژه‌ها داده شده است."))
    idx = 1
    for q in qs:
        for el in a_block(idx, q):
            s.append(el)
        s.append(HRFlowable(width="100%", thickness=0.5, color=HexColor("#E5E7EB"), spaceAfter=6, spaceBefore=4))
        idx += 1
    return s

def answer_space(marks):
    """Dotted writing lines sized to the marks of a question."""
    n = {0.5: 1, 0.75: 1, 1: 2, 1.25: 2, 1.5: 3, 2: 4}.get(marks, 2)
    out = [Spacer(1, 4)]
    for _ in range(n):
        out.append(HRFlowable(width="100%", thickness=0.7, color=HexColor("#B8C2CC"),
                              dash=(1.2, 2.6), spaceBefore=13, spaceAfter=0))
    out.append(Spacer(1, 4))
    return out

def exam_answer_sheet(exam):
    """Blank answer sheet: question numbers with ruled answer boxes (RTL column order)."""
    head_style = S("ahs", fontName="Vazir-Bold", fontSize=10, alignment=TA_CENTER, textColor=white)
    head = [Paragraph(fa("پاسخ / محاسبات"), head_style), Paragraph(fa("سؤال"), head_style),
            Paragraph(fa("پاسخ / محاسبات"), head_style), Paragraph(fa("سؤال"), head_style)]
    data = [head]
    items = exam["items"]
    half = (len(items) + 1) // 2
    num_style = S("ahn", fontSize=9.5, alignment=TA_CENTER)
    for i in range(half):
        right = items[i]
        left = items[i + half] if i + half < len(items) else None
        lnum = Paragraph(fa(f"{i+half+1} ({left[1]})"), num_style) if left else ""
        data.append(["", lnum, "", Paragraph(fa(f"{i+1} ({right[1]})"), num_style)])
    widths = [60 * mm, 20 * mm, 60 * mm, 20 * mm]
    t = Table(data, colWidths=widths, rowHeights=[22] + [74] * half)
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), HexColor("#0F2A43")),
        ("GRID", (0, 0), (-1, -1), 0.6, HexColor("#9CA3AF")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("ALIGN", (0, 0), (-1, -1), "CENTER"),
        ("BACKGROUND", (1, 1), (1, -1), HexColor("#F3F4F6")),
        ("BACKGROUND", (3, 1), (3, -1), HexColor("#F3F4F6")),
    ]))
    return t

def exam_story(exam, with_answers=True, space=True):
    s = []
    s.append(P(exam["title"], stTitle))
    s.append(P(f"علوم هشتم | فصل‌های ۱ تا ۴ | زمان پیشنهادی: {exam['time']} | نمره کل: ۲۰", stSub))
    s.append(Spacer(1, 6))
    s.append(P("نام و نام خانوادگی: ............................  کلاس: ............  تاریخ: ............", stBody))
    s.append(HRFlowable(width="100%", thickness=1, color=HexColor("#0F2A43"), spaceAfter=8, spaceBefore=6))
    for i, (q, marks, ans, exp) in enumerate(exam["items"], 1):
        head = P(f"سؤال {i} ({marks} نمره): {q}")
        if space:
            s.append(head)
            s.extend(answer_space(marks))
        else:
            s.append(KeepTogether([head, Spacer(1, 10)]))
    if with_answers:
        s.append(PageBreak())
        s.append(P(f"پاسخ‌نامه {exam['title']}", stH1))
        for i, (q, marks, ans, exp) in enumerate(exam["items"], 1):
            s.append(P(f"پاسخ سؤال {i} ({marks} نمره): {ans}", S("ea", fontName="Vazir-Bold", fontSize=11, leading=19, textColor=HexColor("#065F46"))))
            s.append(P("توضیح:", S("ealb", fontName="Vazir-Bold", fontSize=9.5, leading=15, spaceBefore=0, spaceAfter=0, textColor=HexColor("#92400E"))))
            s.append(P(exp, S("eatx", fontSize=10.5, leading=18, spaceBefore=0, spaceAfter=4)))
            s.append(Spacer(1, 6))
    return s

def build_story():
    global PAGEMAP, PREV_MAP
    PREV_MAP = dict(PAGEMAP) if PAGEMAP else PREV_MAP
    PAGEMAP = {}
    story = []
    # Cover
    story.append(img("cover.png", width_mm=170))
    story.append(PageBreak())
    # Title page
    story.append(P("علوم هشتم", stTitle))
    story.append(P("آمادگی جامع امتحان", S("t2", fontName="Vazir-Bold", fontSize=20, leading=30, alignment=TA_CENTER, textColor=HexColor("#0E7C7B"))))
    story.append(P("آموزش کامل، نکات طلایی، بانک سؤالات و آزمون‌های شبیه‌سازی‌شده", stSub))
    story.append(P("فصل‌های ۱ تا ۴ | سال تحصیلی ۱۴۰۵–۱۴۰۶", stSub))
    story.append(Spacer(1, 8))
    story.append(panel("مشخصات کتاب",
        "مطابق کتاب درسی علوم تجربی پایه هشتم (چاپ ۱۴۰۵). فصل‌های ۱ تا ۴: مخلوط و جداسازی مواد، تغییرهای شیمیایی در خدمت زندگی، از درون اتم چه خبر؟ و تنظیم عصبی. "
        "این کتاب شامل آموزش کامل، تحلیل فعالیت‌ها و شکل‌ها، بانک سؤالات چهارسطحی با پاسخ تشریحی، مرور شب امتحان، واژه‌نامه، جدول‌های مقایسه و سه آزمون شبیه‌سازی‌شده ۲۰ نمره‌ای است.",
        bg="#EFF6FF", border="#0F2A43"))
    story.append(Spacer(1, 8))
    story.append(P("نکته مهم درباره منبع", stH3))
    story.append(P("این کتاب بر اساس کتاب درسی رسمی علوم هشتم تنظیم شده است. عنوان فصل‌ها و صفحات (فصل ۱: صفحه ۱، فصل ۲: صفحه ۹، فصل ۳: صفحه ۲۱، فصل ۴: صفحه ۲۸) با چاپ ۱۴۰۵ مطابقت دارد. دسترسی مستقیم به فایل PDF کتاب درسی از سامانه chap.sch.ir در زمان تهیه این کتاب با خطای فنی مواجه بود؛ بنابراین محتوا با چند منبع آموزشی معتبر و گام‌به‌گام‌های هماهنگ با کتاب درسی تطبیق داده شد. سؤال‌های این کتاب «سؤال تمرینی» هستند و ادعایی درباره سابقه امتحانی آن‌ها مطرح نمی‌شود."))
    story.append(PageBreak())
    # How to use
    story.append(bm("how", "راهنمای استفاده از این کتاب", 0))
    story.append(P("راهنمای استفاده از این کتاب", stH1))
    story.append(P("این کتاب برای مطالعه مرحله‌به‌مرحله طراحی شده است:"))
    for t in [
        "۱) ابتدا آموزش هر فصل را با دقت بخوانید و مثال‌ها را خودتان تکرار کنید.",
        "۲) نمودارهای خلاصه تصویری را مرور کنید و ارتباط مفاهیم را درک کنید.",
        "۳) بانک سؤالات را بدون نگاه‌کردن به پاسخ حل کنید؛ از سطح آسان شروع کنید.",
        "۴) پاسخ‌نامه را بخوانید و اشتباه‌هایتان را یادداشت کنید.",
        "۵) بخش مرور شب امتحان و برگه‌های حفظی را در شب قبل از امتحان بخوانید.",
        "۶) سه آزمون شبیه‌سازی‌شده را در زمان واقعی (۶۰ دقیقه) برگزار کنید و نمره خود را محاسبه کنید.",
        "۷) چک‌لیست نهایی را تیک بزنید تا مطمئن شوید همه اهداف را پوشش داده‌اید.",
    ]:
        story.append(P(t))
    story.append(tipbox("برای بهترین نتیجه، هر فصل را در یک جلسه بخوانید و همان روز سؤالات آن را حل کنید. مرور فاصله‌دار (یک روز بعد و یک هفته بعد) ماندگاری را چند برابر می‌کند."))
    story.append(PageBreak())
    # TOC
    story.append(bm("toc", "فهرست مطالب", 0))
    story.append(P("فهرست مطالب", stH1))
    story.append(P("عنوان‌های زیر قابل کلیک هستند؛ با لمس هر عنوان به همان صفحه می‌روید.", stSmall))
    story.append(Spacer(1, 4))
    toc_entries = [
        ("how", "راهنمای استفاده از این کتاب", 0),
        ("road", "نقشه راه مطالعه", 0),
    ]
    ch_names = {1: "مخلوط و جداسازی مواد", 2: "تغییرهای شیمیایی در خدمت زندگی",
                3: "از درون اتم چه خبر؟", 4: "تنظیم عصبی"}
    fa_num = {1: "۱", 2: "۲", 3: "۳", 4: "۴"}
    for n in (1, 2, 3, 4):
        fn_ = fa_num[n]
        toc_entries.append((f"ch{n}-teach", f"فصل {fn_}: {ch_names[n]} — آموزش کامل، فعالیت‌ها و شکل‌ها", 0))
        toc_entries.append((f"ch{n}-vis", f"فصل {fn_} — خلاصه تصویری و نمودارها", 1))
        toc_entries.append((f"ch{n}-q", f"فصل {fn_} — بانک سؤالات (۳۹ سؤال چهارسطحی)", 1))
        toc_entries.append((f"ch{n}-a", f"فصل {fn_} — پاسخ‌نامه تشریحی", 1))
    toc_entries += [
        ("rev", "مرور جامع چهار فصل (شب امتحان)", 0),
        ("gloss", "واژه‌نامه علمی", 0),
        ("comp", "جدول‌های مقایسه", 0),
        ("mist", "اشتباه‌های رایج و دام‌های امتحانی", 0),
        ("mem", "تکنیک‌های حافظه", 0),
        ("exam1", "آزمون شبیه‌سازی‌شده ۱ (۲۰ نمره)", 0),
        ("exam2", "آزمون شبیه‌سازی‌شده ۲ (۲۰ نمره)", 0),
        ("exam3", "آزمون شبیه‌سازی‌شده ۳ (۲۰ نمره)", 0),
        ("memo", "برگه‌های حفظی سریع", 0),
        ("check", "چک‌لیست نهایی امتحان", 0),
        ("src", "منابع و توضیح محدودیت‌ها", 0),
    ]
    story.append(toc_table(toc_entries))
    story.append(PageBreak())
    # Roadmap
    story.append(bm("road", "نقشه راه مطالعه", 0))
    story.append(P("نقشه راه مطالعه", stH1))
    story.append(styled_table(
        ["فصل", "موضوع", "صفحات کتاب", "زمان مطالعه", "اولویت امتحانی"],
        [
            ["۱", "مخلوط و جداسازی مواد", "۱ تا ۸", "۳–۴ ساعت", "بالا"],
            ["۲", "تغییرهای شیمیایی در خدمت زندگی", "۹ تا ۲۰", "۳–۴ ساعت", "بالا"],
            ["۳", "از درون اتم چه خبر؟", "۲۱ تا ۲۷", "۴–۵ ساعت", "بسیار بالا"],
            ["۴", "تنظیم عصبی", "۲۸ تا ۳۴", "۳–۴ ساعت", "بالا"],
        ], col_widths=[16, 62, 30, 30, 32]))
    story.append(Spacer(1, 6))
    story.append(P("ترتیب پیشنهادی: فصل ۱ (پایه شیمی)، سپس فصل ۲ (واکنش و انرژی)، سپس فصل ۳ (ساختار اتم) و در پایان فصل ۴ (زیست‌شناسی). فصل ۳ به دلیل سؤال‌های محاسباتی، بیشترین نمره را دارد؛ آن را با تمرین بیشتری بخوانید."))
    story.append(Spacer(1, 8))
    story.append(P("برنامه پیشنهادی ۷ روزه", stH2))
    story.append(styled_table(
        ["روز", "کار اصلی", "زمان", "خروجی روز"],
        [
            ["۱", "فصل ۱: آموزش کامل + تحلیل فعالیت‌ها", "۹۰ دقیقه", "خلاصه یک‌صفحه‌ای از روش‌های جداسازی"],
            ["۲", "فصل ۱: بانک سؤالات + پاسخ‌نامه", "۷۵ دقیقه", "رفع اشتباه‌های سطح ۲ و ۳"],
            ["۳", "فصل ۲: آموزش کامل + آزمایش‌ها", "۹۰ دقیقه", "جدول تفاوت تغییر فیزیکی و شیمیایی"],
            ["۴", "فصل ۳: آموزش کامل + تمرین Z و A", "۱۰۰ دقیقه", "حل ۱۰ سؤال محاسباتی اتم"],
            ["۵", "فصل ۴: آموزش کامل + شکل‌های نورون", "۹۰ دقیقه", "رسم کمان انعکاس از حافظه"],
            ["۶", "مرور جامع + واژه‌نامه + اشتباه‌های رایج", "۶۰ دقیقه", "تیک‌زدن چک‌لیست"],
            ["۷", "آزمون شبیه‌سازی‌شده ۱ و ۲ در زمان واقعی", "۱۲۰ دقیقه", "تصحیح با پاسخ‌نامه و محاسبه نمره"],
        ], col_widths=[14, 66, 26, 64]))
    story.append(Spacer(1, 6))
    story.append(panel("اولویت‌های امتحانی این چهار فصل", [
        "۱) فصل ۳ (عدد اتمی، عدد جرمی، ایزوتوپ، یون، آرایش الکترونی): بیشترین سؤال محاسباتی و بیشترین احتمال خطا.",
        "۲) فصل ۱ (تشخیص همگن/ناهمگن و انتخاب روش جداسازی): سؤال‌های چهارگزینه‌ای پرتکرار.",
        "۳) فصل ۲ (نشانه‌های تغییر شیمیایی، مثلث آتش، کاتالیزگر): سؤال‌های تعریف و مثال.",
        "۴) فصل ۴ (وظیفه بخش‌های مغز، جهت پیام نورون، کمان انعکاس): سؤال‌های شکل‌محور.",
    ], bg="#EFF6FF", border="#0F2A43"))
    story.append(PageBreak())

    for ch in CHAPTERS:
        n = ch["num"]
        story.append(bm(f"ch{n}-teach", f"فصل {fa_num[n]}: {ch['title']}", 0))
        story.extend(chapter_teaching(ch))
        story.append(PageBreak())
        story.append(bm(f"ch{n}-vis", f"فصل {fa_num[n]} — خلاصه تصویری", 1))
        story.extend(chapter_visuals(ch))
        story.append(PageBreak())
        story.append(bm(f"ch{n}-q", f"فصل {fa_num[n]} — بانک سؤالات", 1))
        story.extend(chapter_questions(ch, QMAP[ch["num"]]))
        story.append(PageBreak())
        story.append(bm(f"ch{n}-a", f"فصل {fa_num[n]} — پاسخ‌نامه", 1))
        story.extend(chapter_answers(ch, QMAP[ch["num"]]))
        story.append(PageBreak())

    # Revision
    story.append(P("مرور جامع چهار فصل (شب امتحان)", stH1))
    story.insert(len(story)-1, bm("rev", "مرور جامع چهار فصل (شب امتحان)", 0))
    story.append(P("این بخش برای شب قبل از امتحان است؛ همه نکات حیاتی در یک نگاه."))
    for ch in CHAPTERS:
        story.append(P(f"فصل {ch['num']}: {ch['title']}", stH2))
        for k in ch["key_points"]:
            story.append(P(f"• {k}"))
    story.append(PageBreak())
    # Glossary
    story.append(P("واژه‌نامه علمی", stH1))
    story.insert(len(story)-1, bm("gloss", "واژه‌نامه علمی", 0))
    story.append(styled_table(["اصطلاح", "تعریف"], [[a, b] for a, b in GLOSSARY], col_widths=[45, 125]))
    story.append(PageBreak())
    # Comparisons
    story.append(P("جدول‌های مقایسه", stH1))
    story.insert(len(story)-1, bm("comp", "جدول‌های مقایسه", 0))
    story.append(styled_table(["مفهوم اول", "مفهوم دوم", "ویژگی اول", "ویژگی دوم"],
                               [[a, b, c, d] for a, b, c, d in COMPARISONS], col_widths=[30, 30, 55, 55]))
    story.append(PageBreak())
    # Mistakes
    story.append(P("اشتباه‌های رایج و دام‌های امتحانی", stH1))
    story.insert(len(story)-1, bm("mist", "اشتباه‌های رایج", 0))
    story.append(styled_table(["اشتباه رایج", "حقیقت"], [[a, b] for a, b in MISTAKES], col_widths=[60, 110]))
    story.append(PageBreak())
    # Memory
    story.append(P("تکنیک‌های حافظه", stH1))
    story.insert(len(story)-1, bm("mem", "تکنیک‌های حافظه", 0))
    for title, desc in MEMORY:
        story.append(panel(title, desc, bg="#F5F3FF", border="#6D28D9", title_color="#6D28D9"))
        story.append(Spacer(1, 4))
    story.append(PageBreak())
    # Exams
    for idx, ex in enumerate([EXAM1, EXAM2, EXAM3], 1):
        story.append(bm(f"exam{idx}", ex["title"], 0))
        story.extend(exam_story(ex, with_answers=True))
        story.append(PageBreak())
    # Memorization sheets
    story.append(P("برگه‌های حفظی سریع", stH1))
    story.insert(len(story)-1, bm("memo", "برگه‌های حفظی سریع", 0))
    story.append(P("این برگه‌ها را چاپ کنید و در شب امتحان مرور کنید."))
    for ch in CHAPTERS:
        story.append(P(f"برگه فصل {ch['num']}: {ch['title']}", stH2))
        for k in ch["key_points"]:
            story.append(P(f"[ ] {k}"))
    story.append(PageBreak())
    # Checklist
    story.append(P("چک‌لیست نهایی امتحان", stH1))
    story.insert(len(story)-1, bm("check", "چک‌لیست نهایی امتحان", 0))
    story.append(P("هر مورد را که با اطمینان می‌دانید تیک بزنید. اگر کمتر از ۱۵ مورد را تیک زدید، بیشتر مطالعه کنید."))
    for c in CHECKLIST:
        story.append(P(f"[ ] {c}"))
    story.append(PageBreak())
    # Sources
    story.append(P("منابع", stH1))
    story.insert(len(story)-1, bm("src", "منابع", 0))
    story.append(P("۱) کتاب درسی علوم تجربی پایه هشتم، چاپ ۱۴۰۵، سامانه chap.sch.ir (عنوان فصل‌ها و صفحات: فصل ۱ صفحه ۱، فصل ۲ صفحه ۹، فصل ۳ صفحه ۲۱، فصل ۴ صفحه ۲۸). دسترسی مستقیم به PDF در زمان تهیه با خطای فنی مواجه بود و این محدودیت در صفحه مشخصات کتاب اعلام شده است."))
    story.append(P("۲) منابع آموزشی هماهنگ با کتاب درسی برای تطبیق تعریف‌ها، فعالیت‌ها و شکل‌ها (آموزش فصل‌های علوم هشتم و گام‌به‌گام‌های معتبر)."))
    story.append(P("۳) سؤال‌های این کتاب، سؤال‌های تمرینی و پیش‌بینی‌شده‌اند و بر اساس اهداف یادگیری کتاب درسی ساخته شده‌اند؛ ادعایی درباره سابقه امتحانی آن‌ها مطرح نمی‌شود."))
    story.append(P("۴) فونت فارسی: وزیرمتن (Vazirmatn). نمودارها با پایتون و مات‌پلات‌لیب تولید شده‌اند."))
    story.append(P("موفق باشید! با مطالعه منظم این کتاب، آمادگی کامل برای امتحان علوم هشتم را خواهید داشت."))
    story.append(PageBreak())
    story.append(Spacer(1, 60))
    story.append(P("پایان", S("endt", fontName="Vazir-Bold", fontSize=30, leading=44, alignment=TA_CENTER, textColor=HexColor("#0F2A43"))))
    story.append(Spacer(1, 10))
    story.append(P("علوم هشتم | آمادگی جامع امتحان | فصل‌های ۱ تا ۴", stSub))
    story.append(Spacer(1, 18))
    story.append(panel("پیام پایانی",
        "دانشی که با تمرین و پرسش همراه شود، در ذهن می‌ماند. اگر سؤالی را اشتباه زدید، آن را یک شکست ندانید؛ آن نقطه، دقیق‌ترین نقشه پیشرفت شماست. "
        "شب امتحان فقط برگه‌های حفظی، واژه‌نامه و جدول‌های مقایسه را مرور کنید و زودتر بخوابید؛ مغز سالم بهتر یادآوری می‌کند.",
        bg="#E0F2F2", border="#0E7C7B", title_color="#0E7C7B"))
    story.append(Spacer(1, 16))
    story.append(P("نسخه ۱٫۰ | سال تحصیلی ۱۴۰۵–۱۴۰۶ | تهیه‌شده با فونت وزیرمتن و نمودارهای علمی اختصاصی", stCap))

    return story

def build_extras():
    # Separate exam papers (no answers)
    papers = []
    papers.append(P("آزمون‌های شبیه‌سازی‌شده علوم هشتم | فصل‌های ۱ تا ۴", stTitle))
    papers.append(P("سه آزمون ۲۰ نمره‌ای، هر یک ۶۰ دقیقه | پاسخ‌نامه تشریحی در فایل جداگانه Science8_Answer_Key.pdf", stSub))
    papers.append(Spacer(1, 10))
    papers.append(panel("دستور برگزاری آزمون", [
        "۱) هر آزمون را در یک جلسه ۶۰ دقیقه‌ای و بدون کتاب برگزار کنید.",
        "۲) پاسخ سؤال‌های چهارگزینه‌ای، درست/نادرست و جای خالی را در برگه پاسخ بنویسید.",
        "۳) برای سؤال‌های تشریحی از محل نقطه‌چین زیر هر سؤال استفاده کنید.",
        "۴) پس از پایان، با پاسخ‌نامه تشریحی تصحیح کنید و نمره خود را از ۲۰ محاسبه کنید.",
    ], bg="#EFF6FF", border="#0F2A43"))
    papers.append(PageBreak())
    for ex in [EXAM1, EXAM2, EXAM3]:
        papers.extend(exam_story(ex, with_answers=False))
        papers.append(PageBreak())
        papers.append(P(f"پاسخ‌برگ {ex['title']}", stH1))
        papers.append(P(f"نام و نام خانوادگی: ............................  کلاس: ............  نمره: ............ از ۲۰", stBody))
        papers.append(Spacer(1, 6))
        papers.append(exam_answer_sheet(ex))
        papers.append(PageBreak())
    build(os.path.join(OUT, "Science8_Exam_Papers.pdf"), papers, title="آزمون‌های علوم هشتم")

    # Separate answer key
    keys = []
    for ex in [EXAM1, EXAM2, EXAM3]:
        keys.append(P(f"پاسخ‌نامه {ex['title']}", stH1))
        for i, (q, marks, ans, exp) in enumerate(ex["items"], 1):
            keys.append(P(f"پاسخ سؤال {i} ({marks} نمره): {ans}", S("ea2", fontName="Vazir-Bold", fontSize=11, leading=19, textColor=HexColor("#065F46"))))
            keys.append(P("توضیح:", S("ea3", fontName="Vazir-Bold", fontSize=9.5, leading=15, spaceBefore=0, spaceAfter=0, textColor=HexColor("#92400E"))))
            keys.append(P(exp, S("ea4", fontSize=10.5, leading=18, spaceBefore=0, spaceAfter=4)))
            keys.append(Spacer(1, 6))
        keys.append(PageBreak())
    build(os.path.join(OUT, "Science8_Answer_Key.pdf"), keys, title="پاسخ‌نامه آزمون‌های علوم هشتم")

def main():
    target = os.path.join(OUT, "Science8_Complete_Exam_Guide_1405-1406.pdf")
    # two passes: first collects real page numbers for the TOC, second renders them
    for attempt in range(2):
        story = build_story()
        build(target, story)
        print(f"pass {attempt + 1}: {len(PAGEMAP)} sections located")
    build_extras()
    print("DONE")

if __name__ == "__main__":
    main()
