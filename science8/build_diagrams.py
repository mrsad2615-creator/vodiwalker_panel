#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate all scientific diagrams for Science 8 book (Ch 1-4) as PNGs."""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.font_manager as fm
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Circle, Ellipse, Rectangle, Polygon
import arabic_reshaper
from bidi.algorithm import get_display
import os

BASE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(BASE, "assets")
os.makedirs(OUT, exist_ok=True)

FONT_PATH = os.path.join(BASE, "fonts", "Vazirmatn-Regular.ttf")
FONT_BOLD = os.path.join(BASE, "fonts", "Vazirmatn-Bold.ttf")
for p in [FONT_PATH, FONT_BOLD]:
    fm.fontManager.addfont(p)
plt.rcParams["font.family"] = "Vazirmatn"
plt.rcParams["axes.unicode_minus"] = False

def fa(t):
    return get_display(arabic_reshaper.reshape(t))

def save(fig, name):
    path = os.path.join(OUT, name)
    fig.savefig(path, dpi=220, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print("saved", name)

def box(ax, x, y, w, h, text, fc, ec="#333333", fs=11, bold=False, tc="black"):
    b = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.02,rounding_size=0.08",
                       facecolor=fc, edgecolor=ec, linewidth=1.2)
    ax.add_patch(b)
    ax.text(x + w/2, y + h/2, fa(text), ha="center", va="center", fontsize=fs,
            color=tc, weight="bold" if bold else "normal", wrap=True)
    return b

def arrow(ax, p1, p2, color="#333333"):
    a = FancyArrowPatch(p1, p2, arrowstyle="->", mutation_scale=14, color=color, linewidth=1.4)
    ax.add_patch(a)

# ---------- 1. Ch1 mixture classification ----------
def d_ch1_class():
    fig, ax = plt.subplots(figsize=(10, 5.2))
    ax.set_xlim(0, 10); ax.set_ylim(0, 5.2); ax.axis("off")
    ax.set_title(fa("طبقه‌بندی مخلوط‌ها"), fontsize=16, weight="bold", pad=12)
    box(ax, 3.4, 4.2, 3.2, 0.7, "مخلوط", "#E0F2F2", fs=13, bold=True)
    box(ax, 0.6, 2.9, 3.6, 0.8, "همگن (محلول)", "#CDEEDB", fs=12, bold=True)
    box(ax, 5.8, 2.9, 3.6, 0.8, "ناهمگن (غیریکنواخت)", "#FFE3C2", fs=12, bold=True)
    arrow(ax, (4.2, 4.2), (2.4, 3.7)); arrow(ax, (5.8, 4.2), (7.6, 3.7))
    box(ax, 0.3, 1.5, 4.2, 1.1, "اجزا یکنواخت پخش شده‌اند\nقابل تشخیص نیستند\nمثال: آب‌نمک، هوا، نوشابه", "#F2FBF5", fs=10)
    box(ax, 5.5, 1.5, 4.2, 1.1, "اجزا یکنواخت نیستند\nقابل تشخیص‌اند\nمثال: خاک، سالاد، دوغ", "#FFF6EA", fs=10)
    box(ax, 0.3, 0.2, 4.2, 1.0, "سوسپانسیون: ذرات جامد در مایع\n(خاکشیر، شربت معده) — ته‌نشین می‌شود", "#EAF2FF", fs=9)
    box(ax, 5.5, 0.2, 4.2, 1.0, "کلوئید: بین محلول و سوسپانسیون\n(ژله، مه) — ذرات ریز پراکنده", "#F3E8FF", fs=9)
    save(fig, "ch1_classification.png")

# ---------- 2. Ch1 separation flowchart ----------
def d_ch1_sep():
    fig, ax = plt.subplots(figsize=(10, 6.0))
    ax.set_xlim(0, 10); ax.set_ylim(0, 6); ax.axis("off")
    ax.set_title(fa("روش جداسازی را چگونه انتخاب کنیم؟ (بر اساس ویژگی متفاوت)"), fontsize=14, weight="bold", pad=12)
    box(ax, 3.2, 5.0, 3.6, 0.7, "مخلوط را بررسی کن", "#E0F2F2", fs=12, bold=True)
    items = [
        (0.2, 3.4, "اندازه ذرات متفاوت؟\nصاف‌کردن / الک", "#DFF3FF"),
        (2.6, 3.4, "چگالی متفاوت؟\nسرریز / گریزانه", "#FFF3D6"),
        (5.0, 3.4, "نقطه جوش متفاوت؟\nتقطیر", "#FFE3E3"),
        (7.4, 3.4, "حلالیت متفاوت؟\nتبلور / استخراج", "#E6F7E6"),
    ]
    for x, y, t, c in items:
        box(ax, x, y, 2.2, 1.1, t, c, fs=10)
        arrow(ax, (5.0, 5.0), (x + 1.1, 4.5))
    box(ax, 0.2, 1.7, 2.2, 1.2, "مثال: آب و ماسه\nآب و برنج", "white", fs=9)
    box(ax, 2.6, 1.7, 2.2, 1.2, "مثال: آب و روغن\nچربی شیر", "white", fs=9)
    box(ax, 5.0, 1.7, 2.2, 1.2, "مثال: آب و الکل\nاجزای نفت خام", "white", fs=9)
    box(ax, 7.4, 1.7, 2.2, 1.2, "مثال: آب و نمک\nرنگ‌های گیاهی", "white", fs=9)
    box(ax, 1.5, 0.2, 7.0, 1.1, "روش‌های دیگر: آهن‌ربا (آهن از براده چوب)  •  کروماتوگرافی (رنگ‌ها)  •  دیالیز (خون)", "#F3E8FF", fs=10, bold=True)
    save(fig, "ch1_separation.png")

# ---------- 3. Ch1 distillation ----------
def d_ch1_distill():
    fig, ax = plt.subplots(figsize=(9, 5.4))
    ax.set_xlim(0, 10); ax.set_ylim(0, 5.4); ax.axis("off")
    ax.set_title(fa("تقطیر ساده: جداسازی بر اساس نقطه جوش"), fontsize=14, weight="bold", pad=10)
    # flask
    ax.add_patch(Rectangle((1.2, 0.8), 2.2, 1.6, facecolor="#DFF3FF", edgecolor="#333", lw=1.5))
    ax.text(2.3, 1.4, fa("مخلوط\n(آب + الکل)"), ha="center", va="center", fontsize=10)
    ax.add_patch(Rectangle((2.0, 2.4), 0.6, 1.2, facecolor="white", edgecolor="#333", lw=1.5))
    ax.text(2.3, 3.9, fa("بخار بالا می‌رود"), ha="center", fontsize=9)
    # condenser
    ax.add_patch(Rectangle((3.6, 2.6), 3.4, 0.9, facecolor="#E6F7E6", edgecolor="#333", lw=1.5))
    ax.text(5.3, 3.05, fa("مبدّل (سردکننده)"), ha="center", va="center", fontsize=10)
    ax.text(5.3, 3.75, fa("ورود آب سرد"), ha="center", fontsize=8)
    ax.text(5.3, 2.25, fa("خروج آب گرم"), ha="center", fontsize=8)
    # receiver
    ax.add_patch(Rectangle((7.4, 0.8), 1.8, 1.6, facecolor="#FFF3D6", edgecolor="#333", lw=1.5))
    ax.text(8.3, 1.4, fa("مایع خالص\nجمع می‌شود"), ha="center", va="center", fontsize=10)
    arrow(ax, (2.6, 3.2), (3.6, 3.05)); arrow(ax, (7.0, 3.05), (7.6, 2.4))
    ax.text(5, 0.25, fa("نکته: جزء با نقطه جوش پایین‌تر زودتر تبخیر و جدا می‌شود."), ha="center", fontsize=10, style="italic")
    save(fig, "ch1_distillation.png")

# ---------- 4. Ch1 solution components ----------
def d_ch1_solution():
    fig, ax = plt.subplots(figsize=(9, 4.6))
    ax.set_xlim(0, 10); ax.set_ylim(0, 4.6); ax.axis("off")
    ax.set_title(fa("اجزای محلول: حلال و حل‌شونده"), fontsize=14, weight="bold", pad=10)
    box(ax, 0.4, 1.6, 4.2, 1.8, "حلال: ماده‌ای که بیشتر است\n(معمولاً مایع)\nمثال: آب در آب‌نمک", "#DFF3FF", fs=11)
    box(ax, 5.4, 1.6, 4.2, 1.8, "حل‌شونده: ماده‌ای که کمتر است\nو در حلال پخش می‌شود\nمثال: نمک در آب‌نمک", "#FFE3C2", fs=11)
    arrow(ax, (4.6, 2.5), (5.4, 2.5))
    ax.text(5, 3.9, fa("محلول = حلال + حل‌شونده  (مخلوط همگن)"), ha="center", fontsize=12, weight="bold")
    ax.text(5, 0.7, fa("در محلول‌های گازی مثل هوا، نیتروژن حلال و اکسیژن حل‌شونده است."), ha="center", fontsize=10)
    save(fig, "ch1_solution.png")

# ---------- 5. Ch2 fire triangle + change types ----------
def d_ch2_fire():
    fig, ax = plt.subplots(figsize=(9, 5.2))
    ax.set_xlim(0, 10); ax.set_ylim(0, 5.2); ax.axis("off")
    ax.set_title(fa("مثلث آتش: سه شرط سوختن"), fontsize=15, weight="bold", pad=10)
    # triangle
    tri = Polygon([[5, 4.3], [2.5, 1.2], [7.5, 1.2]], closed=True, facecolor="#FFF3D6", edgecolor="#C7512C", lw=2)
    ax.add_patch(tri)
    ax.text(5, 3.3, fa("گرما\n(انرژی فعال‌سازی)"), ha="center", va="center", fontsize=11, weight="bold")
    ax.text(3.6, 1.7, fa("ماده سوختی"), ha="center", fontsize=11, weight="bold")
    ax.text(6.4, 1.7, fa("اکسیژن"), ha="center", fontsize=11, weight="bold")
    ax.text(5, 0.45, fa("حذف یکی از سه ضلع = خاموش شدن آتش"), ha="center", fontsize=11, weight="bold",
            bbox=dict(boxstyle="round,pad=0.4", facecolor="#FFE3E3", edgecolor="#C7512C"))
    save(fig, "ch2_fire_triangle.png")

def d_ch2_change():
    fig, ax = plt.subplots(figsize=(10, 4.8))
    ax.set_xlim(0, 10); ax.set_ylim(0, 4.8); ax.axis("off")
    ax.set_title(fa("تغییر فیزیکی در برابر تغییر شیمیایی"), fontsize=14, weight="bold", pad=10)
    box(ax, 0.4, 2.6, 4.4, 1.2, "تغییر فیزیکی: ماده جدید ساخته نمی‌شود\n(ذوب، انجماد، تبخیر، برش کاغذ)", "#DFF3FF", fs=10)
    box(ax, 5.2, 2.6, 4.4, 1.2, "تغییر شیمیایی: ماده جدید ساخته می‌شود\n(سوختن، زنگ‌زدن، ترش‌شدن شیر)", "#FFE3E3", fs=10)
    box(ax, 0.4, 0.6, 4.4, 1.5, "نشانه‌ها: تغییر رنگ، تولید گاز،\nتشکیل رسوب، آزاد/گرفته‌شدن گرما", "white", fs=10)
    box(ax, 5.2, 0.6, 4.4, 1.5, "واکنش‌دهنده‌ها به فرآورده‌ها تبدیل می‌شوند\nانرژی شیمیایی آزاد یا گرفته می‌شود", "white", fs=10)
    save(fig, "ch2_change_compare.png")

# ---------- 6. Ch3 atom model ----------
def d_ch3_atom():
    fig, ax = plt.subplots(figsize=(9, 5.6))
    ax.set_xlim(0, 10); ax.set_ylim(0, 5.6); ax.axis("off")
    ax.set_title(fa("مدل اتم: هسته و لایه‌های الکترونی (مثال: کربن)"), fontsize=14, weight="bold", pad=10)
    # nucleus
    ax.add_patch(Circle((5, 2.9), 0.85, facecolor="#C7512C", edgecolor="#7A2E12", lw=2))
    ax.text(5, 2.9, fa("هسته"), ha="center", va="center", fontsize=11, weight="bold", color="white")
    ax.text(5, 2.25, fa("۶ پروتون (+)  •  ۶ نوترون (خنثی)"), ha="center", fontsize=10)
    # shells
    for r, n, lab in [(1.7, 2, "لایه ۱: حداکثر ۲ الکترون"), (2.7, 4, "لایه ۲: حداکثر ۸ الکترون")]:
        c = Circle((5, 2.9), r, facecolor="none", edgecolor="#4338CA", lw=1.6, linestyle="--")
        ax.add_patch(c)
        import math
        for i in range(n):
            ang = math.radians(45 + i * (360 / n))
            x, y = 5 + r * math.cos(ang), 2.9 + r * math.sin(ang)
            ax.add_patch(Circle((x, y), 0.22, facecolor="#4338CA", edgecolor="white", lw=1.2))
            ax.text(x, y, "e", ha="center", va="center", fontsize=9, color="white", weight="bold")
    ax.text(8.6, 4.6, fa("الکترون (−)\nدور هسته می‌چرخد"), ha="center", fontsize=10,
            bbox=dict(boxstyle="round,pad=0.4", facecolor="#E0E7FF", edgecolor="#4338CA"))
    ax.text(1.4, 4.6, fa("بیشتر جرم اتم\nدر هسته است"), ha="center", fontsize=10,
            bbox=dict(boxstyle="round,pad=0.4", facecolor="#FFE3E3", edgecolor="#C7512C"))
    ax.text(5, 0.35, fa("اتم خنثی است: تعداد پروتون = تعداد الکترون"), ha="center", fontsize=11, weight="bold")
    save(fig, "ch3_atom_model.png")

def d_ch3_isotopes():
    fig, ax = plt.subplots(figsize=(10, 4.6))
    ax.set_xlim(0, 10); ax.set_ylim(0, 4.6); ax.axis("off")
    ax.set_title(fa("ایزوتوپ‌های کربن: پروتون یکسان، نوترون متفاوت"), fontsize=13, weight="bold", pad=10)
    data = [("C-12", "۶p + ۶n", "A=12"), ("C-13", "۶p + ۷n", "A=13"), ("C-14", "۶p + ۸n", "A=14")]
    for i, (t, s, a) in enumerate(data):
        x = 0.6 + i * 3.2
        box(ax, x, 2.4, 2.6, 1.2, t, "#E0E7FF", fs=14, bold=True)
        box(ax, x, 1.1, 2.6, 1.0, s + "\n" + a, "white", fs=11)
    ax.text(5, 0.5, fa("ایزوتوپ‌ها: خواص شیمیایی یکسان، جرم و خواص فیزیکی متفاوت"), ha="center", fontsize=11, weight="bold")
    save(fig, "ch3_isotopes.png")

def d_ch3_numbers():
    fig, ax = plt.subplots(figsize=(10, 4.4))
    ax.set_xlim(0, 10); ax.set_ylim(0, 4.4); ax.axis("off")
    ax.set_title(fa("عدد اتمی (Z) و عدد جرمی (A)"), fontsize=14, weight="bold", pad=10)
    box(ax, 0.4, 1.8, 4.4, 1.6, "عدد اتمی Z = تعداد پروتون\nنوع عنصر را مشخص می‌کند\nمثال: سدیم Z=11", "#DFF3FF", fs=11)
    box(ax, 5.2, 1.8, 4.4, 1.6, "عدد جرمی A = پروتون + نوترون\nجرم تقریبی اتم\nمثال: کربن-۱۲: A=12", "#FFE3C2", fs=11)
    ax.text(5, 0.8, fa("A = Z + تعداد نوترون  به  نوترون = A − Z"), ha="center", fontsize=12, weight="bold",
            bbox=dict(boxstyle="round,pad=0.4", facecolor="#FFF3D6", edgecolor="#333"))
    save(fig, "ch3_numbers.png")

# ---------- 7. Ch4 nervous system ----------
def d_ch4_system():
    fig, ax = plt.subplots(figsize=(10, 5.4))
    ax.set_xlim(0, 10); ax.set_ylim(0, 5.4); ax.axis("off")
    ax.set_title(fa("دستگاه عصبی: مرکزی و محیطی"), fontsize=15, weight="bold", pad=10)
    box(ax, 3.2, 4.3, 3.6, 0.7, "دستگاه عصبی", "#EDE9FE", fs=13, bold=True)
    box(ax, 0.5, 2.9, 4.2, 0.9, "مرکزی: مغز + نخاع\nمرکز فرماندهی", "#DDD6FE", fs=11, bold=True)
    box(ax, 5.3, 2.9, 4.2, 0.9, "محیطی: اعصاب\nپیام‌رسان بدن", "#CDEEDB", fs=11, bold=True)
    arrow(ax, (4.4, 4.3), (2.6, 3.8)); arrow(ax, (5.6, 4.3), (7.4, 3.8))
    box(ax, 0.5, 1.2, 4.2, 1.3, "مغز در جمجمه • نخاع در ستون مهره\nدریافت، تفسیر و پاسخ", "white", fs=10)
    box(ax, 5.3, 1.2, 4.2, 1.3, "حسی: محیط به مرکز\nحرکتی: مرکز به اندام", "white", fs=10)
    box(ax, 1.8, 0.2, 6.4, 0.8, "۴۳ جفت عصب: ۱۲ جفت از مغز + ۳۱ جفت از نخاع", "#FFF3D6", fs=10, bold=True)
    save(fig, "ch4_system.png")

def d_ch4_neuron():
    """Neuron drawn right-to-left: dendrites at right, axon terminal at left (Persian reading order)."""
    fig_side = 10
    fig, ax = plt.subplots(figsize=(10, 5.2))
    ax.set_xlim(0, 10); ax.set_ylim(0, 5.2); ax.axis("off")
    ax.set_title(fa("نورون: ساختار و مسیر پیام عصبی"), fontsize=14, weight="bold", pad=12)
    import math
    cx, cy = 5.8, 2.9
    # dendrites (right side)
    for ang in [50, 25, 0, -25, -50]:
        x2 = cx + 1.75 * math.cos(math.radians(ang))
        y2 = cy + 1.75 * math.sin(math.radians(ang))
        ax.plot([cx + 0.85 * math.cos(math.radians(ang)), x2],
                [cy + 0.85 * math.sin(math.radians(ang)), y2],
                color="#6D28D9", lw=2.2, solid_capstyle="round")
        ax.add_patch(Circle((x2, y2), 0.07, facecolor="#6D28D9", edgecolor="none"))
    ax.add_patch(Circle((cx, cy), 0.95, facecolor="#DDD6FE", edgecolor="#6D28D9", lw=2.2))
    ax.text(cx, cy + 0.12, fa("جسم"), ha="center", va="center", fontsize=10, weight="bold")
    ax.text(cx, cy - 0.28, fa("یاخته‌ای"), ha="center", va="center", fontsize=9.5, weight="bold")
    # axon toward left
    ax.plot([cx - 0.95, 1.9], [cy, cy], color="#6D28D9", lw=4.5, solid_capstyle="round")
    ax.add_patch(Circle((1.65, cy), 0.34, facecolor="#FDE68A", edgecolor="#92400E", lw=1.6))
    ax.annotate("", xy=(2.4, cy), xytext=(3.0, cy),
                arrowprops=dict(arrowstyle="-|>", color="#4C1D95", lw=2, mutation_scale=16))
    # labels
    ax.text(8.6, 4.5, fa("دارینه (دندریت)"), ha="center", fontsize=10.5, weight="bold",
            bbox=dict(boxstyle="round,pad=0.3", facecolor="#F5F3FF", edgecolor="#6D28D9"))
    ax.text(8.6, 4.1, fa("پیام را دریافت می‌کند"), ha="center", fontsize=8.5)
    ax.plot([7.55, 7.1], [4.05, 3.75], color="#6D28D9", lw=0.9, ls=":")
    ax.text(3.75, 3.45, fa("آسه (آکسون): انتقال پیام"), ha="center", fontsize=10.5, weight="bold")
    ax.text(1.65, cy - 0.72, fa("پایانه آسه"), ha="center", fontsize=9.5)
    ax.text(1.65, 4.35, fa("سیناپس: اتصال به نورون یا ماهیچه"), ha="center", fontsize=9,
            bbox=dict(boxstyle="round,pad=0.3", facecolor="#FFF3D6", edgecolor="#92400E"))
    ax.plot([1.65, 1.65], [3.95, cy + 0.42], color="#92400E", lw=0.9, ls=":")
    ax.text(5.0, 1.05, fa("جهت پیام: دارینه، جسم یاخته‌ای، آسه، پایانه"),
            ha="center", fontsize=11, weight="bold")
    ax.text(5.0, 0.55, fa("پیام عصبی همیشه یک‌طرفه حرکت می‌کند."), ha="center", fontsize=9.5, style="italic")
    save(fig, "ch4_neuron.png")

def d_ch4_brain():
    fig, ax = plt.subplots(figsize=(10, 5.6))
    ax.set_xlim(0, 10); ax.set_ylim(0, 5.6); ax.axis("off")
    ax.set_title(fa("مغز و نخاع: مراکز عصبی"), fontsize=15, weight="bold", pad=10)
    box(ax, 0.4, 3.6, 4.4, 1.2, "مخ (نیمکره‌ها)\nتفکر، حافظه، حرکات ارادی", "#DDD6FE", fs=11)
    box(ax, 5.2, 3.6, 4.4, 1.2, "مخچه\nتعادل و هماهنگی حرکت", "#CDEEDB", fs=11)
    box(ax, 0.4, 1.9, 4.4, 1.2, "ساقه مغز + بصل‌النخاع\nتنفس، ضربان قلب، فشار خون", "#FFE3C2", fs=11)
    box(ax, 5.2, 1.9, 4.4, 1.2, "نخاع (در ستون مهره)\nرابط مغز و بدن + انعکاس", "#DFF3FF", fs=11)
    ax.text(5, 3.42, fa("مغز در جمجمه  •  نخاع از بصل‌النخاع تا کمر"), ha="center", fontsize=10, style="italic")
    box(ax, 1.8, 0.4, 6.4, 1.0, "بصل‌النخاع = «گره حیات»  •  نخاع مرکز انعکاس زانو", "#FFF3D6", fs=10, bold=True)
    save(fig, "ch4_brain.png")

def d_ch4_reflex():
    fig, ax = plt.subplots(figsize=(10, 4.2))
    ax.set_xlim(0, 10); ax.set_ylim(0, 4.2); ax.axis("off")
    ax.set_title(fa("کمان انعکاس: پاسخ سریع بدون دخالت مغز"), fontsize=13.5, weight="bold", pad=12)
    steps = ["محرک\n(جسم داغ)", "گیرنده حسی\nدر پوست", "نخاع\nمرکز انعکاس", "عصب حرکتی", "ماهیچه\n(پاسخ)"]
    colors = ["#FFF3D6", "#EDE9FE", "#DBEAFE", "#EDE9FE", "#FFF3D6"]
    w, gap = 1.75, 0.16
    for i, st in enumerate(steps):
        x = 9.85 - (i + 1) * w - i * gap
        box(ax, x, 1.55, w, 1.35, st, colors[i], fs=9)
        if i < 4:
            ax.annotate("", xy=(x - gap + 0.01, 2.22), xytext=(x - 0.01, 2.22),
                        arrowprops=dict(arrowstyle="-|>", color="#374151", lw=1.8, mutation_scale=15))
    ax.text(5.0, 3.6, fa("مسیر پیام از راست به چپ: بدون رفتن به مغز، پاسخ در نخاع صادر می‌شود."),
            ha="center", fontsize=9.5, style="italic")
    ax.text(5.0, 0.6, fa("مثال: عقب کشیدن دست از جسم داغ، انعکاس زانو، پلک زدن"),
            ha="center", fontsize=11, weight="bold",
            bbox=dict(boxstyle="round,pad=0.45", facecolor="#F5F3FF", edgecolor="#6D28D9"))
    save(fig, "ch4_reflex.png")

# ---------- 8. Concept maps ----------
def d_concept(ch, title, center, branches, fname):
    fig, ax = plt.subplots(figsize=(10, 5.6))
    ax.set_xlim(0, 10); ax.set_ylim(0, 5.6); ax.axis("off")
    ax.set_title(fa(title), fontsize=15, weight="bold", pad=12)
    box(ax, 3.4, 4.4, 3.2, 0.8, center, "#333333", fs=12, bold=True, tc="white")
    cols = ["#DFF3FF", "#FFE3C2", "#E6F7E6", "#F3E8FF"]
    for i, (t, sub) in enumerate(branches):
        x = 0.3 + i * 2.45
        box(ax, x, 2.7, 2.2, 0.9, t, cols[i % 4], fs=10, bold=True)
        box(ax, x, 1.1, 2.2, 1.3, sub, "white", fs=8.5)
        arrow(ax, (5.0, 4.4), (x + 1.1, 3.6))
    save(fig, fname)

# ---------- 9. Cover art ----------
def d_cover():
    fig, ax = plt.subplots(figsize=(8, 11))
    ax.set_xlim(0, 8); ax.set_ylim(0, 11); ax.axis("off")
    # background gradient blocks
    ax.add_patch(Rectangle((0, 0), 8, 11, facecolor="#0F2A43", edgecolor="none"))
    ax.add_patch(Rectangle((0, 7.2), 8, 3.8, facecolor="#123A5C", edgecolor="none"))
    # atom orbits
    import math
    cx, cy = 4, 8.6
    for r, col in [(1.6, "#38BDF8"), (1.15, "#F59E0B"), (0.7, "#A78BFA")]:
        th = [i * math.pi / 12 for i in range(24)]
        ax.plot([cx + r * math.cos(t) for t in th], [cy + r * 0.55 * math.sin(t) for t in th], color=col, lw=1.4, alpha=0.8)
    ax.add_patch(Circle((cx, cy), 0.42, facecolor="#F8FAFC", edgecolor="#38BDF8", lw=2))
    ax.text(cx, cy, "8", ha="center", va="center", fontsize=20, weight="bold", color="#0F2A43")
    for ang in [20, 140, 260]:
        x, y = cx + 1.6 * math.cos(math.radians(ang)), cy + 0.88 * math.sin(math.radians(ang))
        ax.add_patch(Circle((x, y), 0.16, facecolor="#38BDF8", edgecolor="white"))
    # flask + neuron motifs (simple shapes)
    ax.add_patch(Rectangle((1.2, 4.6), 1.4, 1.6, facecolor="#0E7C7B", edgecolor="white", lw=1.5))
    ax.text(1.9, 5.4, fa("مخلوط"), ha="center", va="center", fontsize=9, color="white", weight="bold")
    ax.add_patch(Circle((5.4, 5.4), 0.75, facecolor="#C7512C", edgecolor="white", lw=1.5))
    ax.text(5.4, 5.4, fa("اتم"), ha="center", va="center", fontsize=10, color="white", weight="bold")
    ax.add_patch(Circle((6.6, 5.4), 0.55, facecolor="#6D28D9", edgecolor="white", lw=1.5))
    ax.text(6.6, 5.4, fa("عصب"), ha="center", va="center", fontsize=9, color="white", weight="bold")
    ax.text(4, 3.6, fa("علوم هشتم"), ha="center", fontsize=34, weight="bold", color="white")
    ax.text(4, 2.9, fa("آمادگی جامع امتحان"), ha="center", fontsize=24, weight="bold", color="#FDE68A")
    ax.text(4, 2.2, fa("آموزش کامل • نکات طلایی • بانک سؤالات • آزمون‌های شبیه‌سازی‌شده"), ha="center", fontsize=11, color="#E2E8F0")
    ax.text(4, 1.5, fa("فصل‌های ۱ تا ۴  |  سال تحصیلی ۱۴۰۵–۱۴۰۶"), ha="center", fontsize=12, color="#94A3B8")
    ax.text(4, 0.7, fa("مطابق کتاب درسی علوم تجربی هشتم"), ha="center", fontsize=10, color="#64748B")
    save(fig, "cover.png")

if __name__ == "__main__":
    d_ch1_class(); d_ch1_sep(); d_ch1_distill(); d_ch1_solution()
    d_ch2_fire(); d_ch2_change()
    d_ch3_atom(); d_ch3_isotopes(); d_ch3_numbers()
    d_ch4_system(); d_ch4_neuron(); d_ch4_brain(); d_ch4_reflex()
    d_concept(1, "نقشه مفهومی فصل ۱: مخلوط و جداسازی", "مخلوط",
              [("همگن", "محلول\nآب‌نمک، هوا"), ("ناهمگن", "خاک، سالاد\nسوسپانسیون"), ("جداسازی", "صاف‌کردن، تقطیر\nتبلور، کروماتوگرافی"), ("محلول", "حلال + حل‌شونده\nاشباع")],
              "concept_ch1.png")
    d_concept(2, "نقشه مفهومی فصل ۲: تغییر شیمیایی", "تغییر شیمیایی",
              [("نشانه‌ها", "رنگ، گاز، رسوب\nگرما"), ("سوختن", "مثلث آتش\nواکنش‌دهنده/فرآورده"), ("انرژی", "فعال‌سازی\nگرماده/گرماگیر"), ("کاربرد", "سوخت، باتری\nبدن جانداران")],
              "concept_ch2.png")
    d_concept(3, "نقشه مفهومی فصل ۳: درون اتم", "اتم",
              [("ذره‌ها", "پروتون، نوترون\nالکترون"), ("عددها", "Z و A\nایزوتوپ"), ("لایه‌ها", "۲، ۸، ۱۸...\nیون"), ("جدول", "ترتیب بر اساس Z\nخواص تکراری")],
              "concept_ch3.png")
    d_concept(4, "نقشه مفهومی فصل ۴: تنظیم عصبی", "دستگاه عصبی",
              [("مرکزی", "مغز + نخاع\nفرماندهی"), ("محیطی", "اعصاب حسی/حرکتی\nپیام‌رسانی"), ("نورون", "دارینه، آسه\nسیناپس"), ("انعکاس", "کمان انعکاس\nپاسخ سریع")],
              "concept_ch4.png")
    d_cover()
    print("ALL DIAGRAMS DONE")
