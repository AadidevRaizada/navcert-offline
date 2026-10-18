"""Build the NavCert Offline MAD mini-project report (DOCX + PDF).

Run with the system Python (python-docx, Pillow):
    python docs/report/build_report.py

Steps: draw figures, write the DOCX, convert to PDF with LibreOffice, read the
page of every heading from the PDF (PyMuPDF, from ml/.venv) and rebuild until the
index page numbers match the PDF.
"""
import json
import os
import re
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont
from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK, WD_LINE_SPACING, WD_TAB_ALIGNMENT, WD_TAB_LEADER
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
FIG = os.path.join(HERE, "figures")
SHOTS = os.path.join(REPO, "docs", "screenshots")
OUT_DOCX = os.path.join(HERE, "NavCert_Offline_MAD_Report.docx")
OUT_PDF = os.path.join(HERE, "NavCert_Offline_MAD_Report.pdf")
PAGES_JSON = os.path.join(HERE, "index_pages.json")
SOFFICE = r"C:\Program Files\LibreOffice\program\soffice.exe"
VENV_PY = os.path.join(REPO, "ml", ".venv", "Scripts", "python.exe")
FONT_DIR = r"C:\Windows\Fonts"
FONT = "Times New Roman"
TEXT_WIDTH_IN = 8.27 - 1.25 - 1.0

# --------------------------------------------------------------------------
# Figures
# --------------------------------------------------------------------------

def tfont(size, bold=False):
    return ImageFont.truetype(os.path.join(FONT_DIR, "timesbd.ttf" if bold else "times.ttf"), size)


def fit_font(draw, text, size, bold, max_w):
    while size > 16:
        f = tfont(size, bold)
        if draw.textlength(text, font=f) <= max_w:
            return f
        size -= 2
    return tfont(size, bold)


def centred_text(draw, box, lines, sizes, gap=10):
    """Draw lines centred in a box; the first line is bold. Each line shrinks to fit the box width."""
    x0, y0, x1, y1 = box
    fonts = [fit_font(draw, t, s, i == 0, (x1 - x0) - 24) for i, (t, s) in enumerate(zip(lines, sizes))]
    boxes = [draw.textbbox((0, 0), t, font=f) for t, f in zip(lines, fonts)]
    heights = [b[3] - b[1] for b in boxes]
    total = sum(heights) + gap * (len(lines) - 1)
    y = y0 + (y1 - y0 - total) / 2
    for t, f, b, h in zip(lines, fonts, boxes, heights):
        w = draw.textlength(t, font=f)
        draw.text((x0 + (x1 - x0 - w) / 2, y - b[1]), t, font=f, fill="black")
        y += h + gap


def arrow(draw, p0, p1, width=4, head=20):
    import math
    draw.line([p0, p1], fill="black", width=width)
    ang = math.atan2(p1[1] - p0[1], p1[0] - p0[0])
    a1 = (p1[0] - head * math.cos(ang - 0.45), p1[1] - head * math.sin(ang - 0.45))
    a2 = (p1[0] - head * math.cos(ang + 0.45), p1[1] - head * math.sin(ang + 0.45))
    draw.polygon([p1, a1, a2], fill="black")


def dashed_rect(draw, box, dash=22, gap=14, width=4):
    x0, y0, x1, y1 = box
    for (a, b) in [((x0, y0), (x1, y0)), ((x1, y0), (x1, y1)), ((x1, y1), (x0, y1)), ((x0, y1), (x0, y0))]:
        length = ((b[0] - a[0]) ** 2 + (b[1] - a[1]) ** 2) ** 0.5
        dx, dy = (b[0] - a[0]) / length, (b[1] - a[1]) / length
        s = 0
        while s < length:
            e = min(s + dash, length)
            draw.line([(a[0] + dx * s, a[1] + dy * s), (a[0] + dx * e, a[1] + dy * e)], fill="black", width=width)
            s += dash + gap


def draw_architecture(path):
    # 1800 px wide, printed at about 5.3 in: 40 px is about 8.5 pt.
    W, H = 1800, 1400
    im = Image.new("RGB", (W, H), "white")
    d = ImageDraw.Draw(im)
    f_title, f_label = tfont(46, True), tfont(40)
    B, S = 44, 38  # box title and box text sizes

    dashed_rect(d, (20, 50, W - 20, H - 20))
    label = "Device boundary: no INTERNET permission"
    lw = d.textlength(label, font=f_label)
    d.rectangle((50, 28, 70 + lw, 74), fill="white")
    d.text((60, 28), label, font=f_label, fill="black")

    # Presentation layer
    d.rectangle((55, 95, W - 55, 365), outline="black", width=4)
    d.text((80, 103), "Presentation layer (Compose, Material 3)", font=f_title, fill="black")
    cols = [(80, 640), (665, 1135), (1160, W - 80)]
    texts = [["MainActivity", "Compose host, drawer,", "scan state"],
             ["CropEditorActivity", "explicit intent,", "returns the crop"],
             ["Six drawer screens", "Scan, Documents, Ask,", "Lab, Privacy, About"]]
    for (x0, x1), t in zip(cols, texts):
        b = (x0, 168, x1, 345)
        d.rectangle(b, outline="black", width=3)
        centred_text(d, b, t, [B, S, S])

    # Pipeline
    d.text((60, 385), "Processing pipeline", font=f_title, fill="black")
    d.text((520, 391), "Dispatchers.IO: 1, 2, 7    Dispatchers.Default: 3, 4, 5, 8", font=f_label, fill="black")
    bw, bh, gx = 380, 205, 57
    xs = [55 + i * (bw + gx) for i in range(4)]
    y1, y2 = 455, 725
    row1 = [["1  Capture", "camera or gallery", "Activity Result API"],
            ["2  Crop", "CropEditorActivity", "crop and rotate"],
            ["3  Process", "DocumentScanner", "OpenCV: detect, warp"],
            ["4  OCR", "ML Kit (print)", "PP-OCRv5 (handwriting)"]]
    row2 = [["5  Extract", "rules, then MiniLM,", "then MobileBERT"],
            ["6  Review", "edit text and fields", "notes, highlights"],
            ["7  Store", "DocumentRepository", "JSON + JPEG"],
            ["8  Ask", "chunk retrieval", "MobileBERT answer"]]
    for i, t in enumerate(row1):
        b = (xs[i], y1, xs[i] + bw, y1 + bh)
        d.rectangle(b, outline="black", width=4)
        centred_text(d, b, t, [B, S, S])
        if i < 3:
            arrow(d, (xs[i] + bw + 3, y1 + bh / 2), (xs[i + 1] - 3, y1 + bh / 2))
    for i, t in enumerate(row2):
        c = 3 - i
        b = (xs[c], y2, xs[c] + bw, y2 + bh)
        d.rectangle(b, outline="black", width=4)
        centred_text(d, b, t, [B, S, S])
        if i < 3:
            arrow(d, (xs[c] - 3, y2 + bh / 2), (xs[c - 1] + bw + 3, y2 + bh / 2))
    arrow(d, (xs[3] + bw / 2, y1 + bh + 3), (xs[3] + bw / 2, y2 - 3))

    # Local resources
    top = 990
    d.rectangle((55, top, W - 55, H - 55), outline="black", width=4)
    d.text((80, top + 10), "Local resources (app-private)", font=f_title, fill="black")
    texts = [["Internal storage", "documents/<uuid>.json", "and .jpg; folders.json"],
             ["Cache + FileProvider", "camera captures", "and crops"],
             ["Bundled models", "ML Kit, PP-OCRv5,", "MiniLM, MobileBERT"]]
    for (x0, x1), t in zip(cols, texts):
        b = (x0, top + 75, x1, H - 75)
        d.rectangle(b, outline="black", width=3)
        centred_text(d, b, t, [B, S, S])
    # Store writes to internal storage; models feed the pipeline
    sx = xs[1] + bw / 2
    arrow(d, (sx, y2 + bh + 3), (sx, top + 71))
    mx = xs[3] + bw / 2
    arrow(d, (mx, top + 71), (mx, y2 + bh + 3))
    im.save(path)


def draw_extraction_flow(path):
    W, H = 1900, 470
    im = Image.new("RGB", (W, H), "white")
    d = ImageDraw.Draw(im)
    f_note = tfont(40)
    steps = [["OCR text", "from ML Kit", "or PP-OCRv5"],
             ["Certificate gate", "certificate cue in", "first 1,500 chars"],
             ["Rules", "labels and", "date patterns"],
             ["MiniLM int8", "ONNX, only for", "missing fields"],
             ["MobileBERT", "TFLite fallback", "if ONNX fails"]]
    bw, bh, gx, y = 310, 230, 62, 20
    for i, t in enumerate(steps):
        x = 20 + i * (bw + gx)
        d.rectangle((x, y, x + bw, y + bh), outline="black", width=4)
        centred_text(d, (x, y, x + bw, y + bh), t, [46, 40, 40])
        if i < 4:
            arrow(d, (x + bw + 3, y + bh / 2), (x + bw + gx - 3, y + bh / 2))
    gxc = 20 + (bw + gx) + bw / 2
    arrow(d, (gxc, y + bh + 3), (gxc, 330))
    d.text((gxc - 150, 340), "no cue: no fields", font=f_note, fill="black")
    d.text((gxc - 150, 388), "(abstain)", font=f_note, fill="black")
    x3 = 20 + 2 * (bw + gx) + 40
    d.text((x3, 290), "Model answers are used only when the text has", font=f_note, fill="black")
    d.text((x3, 338), "evidence for the field (issue, authority, expiry),", font=f_note, fill="black")
    d.text((x3, 386), "and a date answer must contain a valid date.", font=f_note, fill="black")
    im.save(path)


def compose_shots(names, path, phone_h=1200):
    phones = []
    for n in names:
        im = Image.open(os.path.join(SHOTS, n)).convert("RGB")
        w = int(im.width * phone_h / im.height)
        phones.append(im.resize((w, phone_h), Image.LANCZOS))
    gap, border, label_h = 70, 3, 110
    W = sum(p.width + 2 * border for p in phones) + gap * (len(phones) - 1)
    H = phone_h + 2 * border + label_h
    sheet = Image.new("RGB", (W, H), "white")
    d = ImageDraw.Draw(sheet)
    f = tfont(76)
    x = 0
    for i, p in enumerate(phones):
        d.rectangle((x, 0, x + p.width + 2 * border - 1, phone_h + 2 * border - 1), fill="black")
        sheet.paste(p, (x + border, border))
        lab = "(%s)" % "abc"[i]
        lw = d.textlength(lab, font=f)
        d.text((x + (p.width + 2 * border - lw) / 2, phone_h + 2 * border + 14), lab, font=f, fill="black")
        x += p.width + 2 * border + gap
    sheet.save(path)


SHOT_SETS = {
    "fig5_1_capture.png": ["01_home.png", "02_capture_or_import.png", "03_crop.png"],
    "fig5_2_preview.png": ["03_preview_aligned.png", "17_lab_geometry.png", "10_lab_histogram.png"],
    "fig5_3_fields.png": ["04_result_text.png", "05_certificate_fields.png", "08_review_save_lab_entry.png"],
    "fig5_4_library_ask.png": ["07_library.png", "06_ask_document.png"],
    "fig5_5_lab.png": ["09_lab_fundamentals.png", "12b_lab_edges_canny.png", "16b_lab_motion_es_vectors.png"],
    "fig5_6_nav_privacy.png": ["20_navigation_drawer.png", "21_privacy.png"],
}


def make_figures():
    os.makedirs(FIG, exist_ok=True)
    draw_architecture(os.path.join(FIG, "fig4_1_architecture.png"))
    draw_extraction_flow(os.path.join(FIG, "fig7_1_extraction_flow.png"))
    for name, shots in SHOT_SETS.items():
        compose_shots(shots, os.path.join(FIG, name))

# --------------------------------------------------------------------------
# References (IEEE), numbered in order of first citation
# --------------------------------------------------------------------------

REFS = {
    "bimco": 'BIMCO and International Chamber of Shipping, "Seafarer Workforce Report 2026: Executive summary," Jun. 2026. [Online]. Available: https://www.bimco.org/media/mxhbsjxb/seafarer-workforce-report-2026-executive-summary.pdf',
    "tokyo": "Tokyo MOU Secretariat, *Annual Report on Port State Control in the Asia-Pacific Region 2024*, 2025. [Online]. Available: https://www.tokyo-mou.org/wp/wp-content/uploads/ANN24-1.pdf",
    "gcaptain": 'M. Schuler, "India cracks down on fraudulent seafarer certificates amid global safety concerns," gCaptain, Jul. 21, 2025. [Online]. Available: https://gcaptain.com/india-cracks-down-on-fraudulent-seafarer-certificates-amid-global-safety-concerns/',
    "imo1665": 'International Maritime Organization, "Guidelines on the use of electronic certificates of seafarers," MSC.1/Circ.1665, Jun. 28, 2023.',
    "compose": 'Google, "Jetpack Compose documentation," Android Developers. Accessed: Oct. 2026. [Online]. Available: https://developer.android.com/develop/ui/compose/documentation',
    "m3compose": 'Google, "Material Design 3 in Compose," Android Developers. Accessed: Oct. 2026. [Online]. Available: https://developer.android.com/develop/ui/compose/designsystems/material3',
    "opencv": 'G. Bradski, "The OpenCV library," *Dr. Dobb\'s Journal of Software Tools*, Nov. 2000.',
    "mlkit": 'Google, "Text recognition v2," ML Kit for Android. Accessed: Oct. 2026. [Online]. Available: https://developers.google.com/ml-kit/vision/text-recognition/v2/android',
    "onnx": 'Microsoft, "ONNX Runtime Mobile," ONNX Runtime documentation. Accessed: Oct. 2026. [Online]. Available: https://onnxruntime.ai/docs/tutorials/mobile/',
    "ppocrv5": 'C. Cui *et al.*, "PP-OCRv5: A specialized 5M-parameter model rivaling billion-parameter vision-language models on OCR tasks," arXiv:2603.24373, Mar. 2026.',
    "litert": 'Google, "LiteRT overview," Google AI Edge documentation. Accessed: Oct. 2026. [Online]. Available: https://developers.google.com/edge/litert',
    "mobilebert": 'Z. Sun *et al.*, "MobileBERT: A compact task-agnostic BERT for resource-limited devices," in *Proc. 58th Annu. Meeting Assoc. Computational Linguistics*, 2020, pp. 2158-2170, doi: 10.18653/v1/2020.acl-main.195.',
    "minilm": 'W. Wang *et al.*, "MiniLM: Deep self-attention distillation for task-agnostic compression of pre-trained transformers," arXiv:2002.10957, 2020.',
    "deepset": 'deepset, "deepset/minilm-uncased-squad2," Hugging Face model card. Accessed: Oct. 2026. [Online]. Available: https://huggingface.co/deepset/minilm-uncased-squad2',
    "squad2": 'P. Rajpurkar, R. Jia, and P. Liang, "Know what you don\'t know: Unanswerable questions for SQuAD," in *Proc. 56th Annu. Meeting ACL (Short Papers)*, 2018, pp. 784-789, doi: 10.18653/v1/P18-2124.',
    "camera": 'Google, "Camera intents," Android Developers. Accessed: Oct. 2026. [Online]. Available: https://developer.android.com/media/camera/camera-intents',
    "storage": 'Google, "Data and file storage overview," Android Developers. Accessed: Oct. 2026. [Online]. Available: https://developer.android.com/training/data-storage',
    "permissions": 'Google, "Request runtime permissions," Android Developers. Accessed: Oct. 2026. [Online]. Available: https://developer.android.com/training/permissions/requesting',
    "room": 'Google, "Save data in a local database using Room," Android Developers. Accessed: Oct. 2026. [Online]. Available: https://developer.android.com/training/data-storage/room',
    "canny": 'J. Canny, "A computational approach to edge detection," *IEEE Trans. Pattern Anal. Mach. Intell.*, vol. PAMI-8, pp. 679-698, 1986, doi: 10.1109/TPAMI.1986.4767851.',
    "clahe": 'K. Zuiderveld, "Contrast limited adaptive histogram equalization," in *Graphics Gems IV*, P. S. Heckbert, Ed. San Diego, CA, USA: Academic Press, 1994, pp. 474-485.',
    "quant": 'B. Jacob *et al.*, "Quantization and training of neural networks for efficient integer-arithmetic-only inference," in *Proc. IEEE/CVF CVPR*, 2018, pp. 2704-2713, doi: 10.1109/CVPR.2018.00286.',
    "ctc": 'A. Graves *et al.*, "Connectionist temporal classification: Labelling unsegmented sequence data with recurrent neural networks," in *Proc. 23rd Int. Conf. Machine Learning (ICML)*, 2006, pp. 369-376, doi: 10.1145/1143844.1143891.',
    "m3": 'Google, "Material Design 3," m3.material.io. Accessed: Oct. 2026. [Online]. Available: https://m3.material.io/foundations',
    "targetapi": 'Google, "Target API level requirements for Google Play apps," Play Console Help. Accessed: Oct. 2026. [Online]. Available: https://support.google.com/googleplay/android-developer/answer/11926878',
    "datasafety": 'Google, "Provide information for Google Play\'s Data safety section," Play Console Help. Accessed: Oct. 2026. [Online]. Available: https://support.google.com/googleplay/android-developer/answer/10787469',
}


class Cites:
    def __init__(self):
        self.order = []

    def sub(self, text):
        def rep(m):
            keys = [k.strip() for k in m.group(1).split(",")]
            nums = []
            for k in keys:
                if k not in REFS:
                    raise KeyError(k)
                if k not in self.order:
                    self.order.append(k)
                nums.append(self.order.index(k) + 1)
            return ", ".join("[%d]" % n for n in nums)
        return re.sub(r"\{c:([^}]+)\}", rep, text)

# --------------------------------------------------------------------------
# DOCX helpers
# --------------------------------------------------------------------------


def set_run_font(run, size=None, bold=None, italic=None, mono=False):
    name = "Courier New" if mono else FONT
    run.font.name = name
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.insert(0, rfonts)
    for a in ("w:ascii", "w:hAnsi", "w:eastAsia", "w:cs"):
        rfonts.set(qn(a), name)
    if size:
        run.font.size = Pt(size)
    if bold is not None:
        run.font.bold = bold
    if italic is not None:
        run.font.italic = italic
    run.font.color.rgb = RGBColor(0, 0, 0)


def style_fonts(style, size, bold=False):
    style.font.name = FONT
    style.font.size = Pt(size)
    style.font.bold = bold
    style.font.color.rgb = RGBColor(0, 0, 0)
    rpr = style.element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.insert(0, rfonts)
    for a in list(rfonts.attrib):
        del rfonts.attrib[a]
    for a in ("w:ascii", "w:hAnsi", "w:eastAsia", "w:cs"):
        rfonts.set(qn(a), FONT)
    color = rpr.find(qn("w:color"))
    if color is not None:
        for a in list(color.attrib):
            if "theme" in a:
                del color.attrib[a]


def add_field(paragraph, instr, size=None):
    r1 = paragraph.add_run()
    fc = OxmlElement("w:fldChar"); fc.set(qn("w:fldCharType"), "begin"); r1._element.append(fc)
    r2 = paragraph.add_run()
    it = OxmlElement("w:instrText"); it.set(qn("xml:space"), "preserve"); it.text = instr; r2._element.append(it)
    r3 = paragraph.add_run()
    fc = OxmlElement("w:fldChar"); fc.set(qn("w:fldCharType"), "separate"); r3._element.append(fc)
    r4 = paragraph.add_run("1")
    r5 = paragraph.add_run()
    fc = OxmlElement("w:fldChar"); fc.set(qn("w:fldCharType"), "end"); r5._element.append(fc)
    for r in (r1, r2, r3, r4, r5):
        set_run_font(r, size)


def shade(cell, fill="D9D9D9"):
    tcpr = cell._element.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear"); shd.set(qn("w:color"), "auto"); shd.set(qn("w:fill"), fill)
    tcpr.append(shd)


def repeat_header(row):
    trpr = row._tr.get_or_add_trPr()
    el = OxmlElement("w:tblHeader"); el.set(qn("w:val"), "true"); trpr.append(el)


def no_split(row):
    trpr = row._tr.get_or_add_trPr()
    el = OxmlElement("w:cantSplit"); el.set(qn("w:val"), "true"); trpr.append(el)


class Report:
    def __init__(self, pages):
        self.pages = pages
        self.doc = Document()
        self.cites = Cites()
        self.headings = []
        self.fig_count = {}
        self.tab_count = {}
        self.chapter = 0
        self._setup()

    # ---- setup ----
    def _setup(self):
        d = self.doc
        sec = d.sections[0]
        sec.page_width, sec.page_height = Inches(8.27), Inches(11.69)
        sec.left_margin, sec.right_margin = Inches(1.25), Inches(1.0)
        sec.top_margin, sec.bottom_margin = Inches(1.0), Inches(1.0)
        sec.footer_distance = Inches(0.5)
        sec.different_first_page_header_footer = True
        fp = sec.footer.paragraphs[0]
        fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        add_field(fp, "PAGE", size=11)

        normal = d.styles["Normal"]
        style_fonts(normal, 12)
        pf = normal.paragraph_format
        pf.line_spacing = 1.15
        pf.space_after = Pt(6)
        pf.space_before = Pt(0)
        pf.widow_control = True
        for name, size, before, after in (("Heading 1", 16, 18, 8), ("Heading 2", 13, 10, 4)):
            st = d.styles[name]
            style_fonts(st, size, bold=True)
            st.font.italic = False
            st.paragraph_format.space_before = Pt(before)
            st.paragraph_format.space_after = Pt(after)
            st.paragraph_format.keep_with_next = True
            st.paragraph_format.line_spacing = 1.0
            st.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
        for name in ("List Bullet", "Caption"):
            style_fonts(d.styles[name], 12)
        for lvl, size in ((1, 11.5), (2, 10.5)):
            try:
                st = d.styles["TOC %d" % lvl]
            except KeyError:
                from docx.enum.style import WD_STYLE_TYPE
                st = d.styles.add_style("TOC %d" % lvl, WD_STYLE_TYPE.PARAGRAPH)
                st.base_style = normal
            style_fonts(st, size, bold=(lvl == 1))
            st.paragraph_format.space_after = Pt(0)
            st.paragraph_format.space_before = Pt(3 if lvl == 1 else 0)
            st.paragraph_format.line_spacing = 1.0
            st.paragraph_format.left_indent = Inches(0 if lvl == 1 else 0.35)
            st.paragraph_format.tab_stops.add_tab_stop(Inches(TEXT_WIDTH_IN), WD_TAB_ALIGNMENT.RIGHT, WD_TAB_LEADER.DOTS)

    # ---- text ----
    def _runs(self, p, text, size=None, base_bold=False):
        text = self.cites.sub(text)
        # tokens: **bold**, *italic*, `mono`
        for tok in re.split(r"(\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`)", text):
            if not tok:
                continue
            if tok.startswith("**"):
                r = p.add_run(tok[2:-2]); set_run_font(r, size, bold=True)
            elif tok.startswith("*"):
                r = p.add_run(tok[1:-1]); set_run_font(r, size, italic=True, bold=base_bold)
            elif tok.startswith("`"):
                r = p.add_run(tok[1:-1]); set_run_font(r, (size or 12) - 1, mono=True, bold=base_bold)
            else:
                r = p.add_run(tok); set_run_font(r, size, bold=base_bold)
        return p

    def para(self, text, align=WD_ALIGN_PARAGRAPH.JUSTIFY, keep=False, after=None):
        p = self.doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT if align == "left" else align
        self._runs(p, text)
        if keep:
            p.paragraph_format.keep_with_next = True
        if after is not None:
            p.paragraph_format.space_after = Pt(after)
        return p

    def bullets(self, items):
        for i, it in enumerate(items):
            p = self.doc.add_paragraph(style="List Bullet")
            p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
            p.paragraph_format.space_after = Pt(3)
            if i < len(items) - 1 and len(items) <= 4:
                p.paragraph_format.keep_with_next = True
            self._runs(p, it)

    def labelled(self, items, indent=0.6):
        """Items as (label, text) with a hanging indent, e.g. FR1."""
        for i, (lab, txt) in enumerate(items):
            p = self.doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
            p.paragraph_format.left_indent = Inches(indent)
            p.paragraph_format.first_line_indent = Inches(-indent)
            p.paragraph_format.space_after = Pt(3)
            p.paragraph_format.tab_stops.add_tab_stop(Inches(indent))
            r = p.add_run(lab + "\t"); set_run_font(r, bold=True)
            self._runs(p, txt)

    def h1(self, title, number=True):
        if number:
            self.chapter += 1
            text = "%d  %s" % (self.chapter, title)
            key = "%d %s" % (self.chapter, title)
        else:
            text, key = title, title
        p = self.doc.add_heading(level=1)
        if number and self.chapter == 1:
            p.paragraph_format.page_break_before = True
        r = p.add_run(text); set_run_font(r, 16, bold=True)
        self.headings.append((1, key, text))
        self.sub = 0

    def h2(self, title):
        self.sub += 1
        text = "%d.%d  %s" % (self.chapter, self.sub, title)
        key = "%d.%d %s" % (self.chapter, self.sub, title)
        p = self.doc.add_heading(level=2)
        r = p.add_run(text); set_run_font(r, 13, bold=True)
        self.headings.append((2, key, text))

    def page_break(self):
        p = self.doc.add_paragraph()
        p.paragraph_format.space_after = Pt(0)
        p.add_run().add_break(WD_BREAK.PAGE)

    # ---- figures and tables ----
    def figure(self, filename, caption, width_in):
        n = self.fig_count.get(self.chapter, 0) + 1
        self.fig_count[self.chapter] = n
        p = self.doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.keep_with_next = True
        p.paragraph_format.space_before = Pt(6)
        p.paragraph_format.space_after = Pt(4)
        p.paragraph_format.line_spacing = 1.0
        p.add_run().add_picture(os.path.join(FIG, filename), width=Inches(width_in))
        c = self.doc.add_paragraph()
        c.alignment = WD_ALIGN_PARAGRAPH.CENTER
        c.paragraph_format.space_after = Pt(10)
        c.paragraph_format.line_spacing = 1.0
        r = c.add_run("Fig. %d.%d  " % (self.chapter, n)); set_run_font(r, 11, bold=True)
        self._runs(c, caption, size=11)

    def table(self, caption, header, rows, widths, size=10.5, bold_first_col=False):
        n = self.tab_count.get(self.chapter, 0) + 1
        self.tab_count[self.chapter] = n
        c = self.doc.add_paragraph()
        c.alignment = WD_ALIGN_PARAGRAPH.CENTER
        c.paragraph_format.keep_with_next = True
        c.paragraph_format.space_before = Pt(6)
        c.paragraph_format.space_after = Pt(4)
        c.paragraph_format.line_spacing = 1.0
        r = c.add_run("Table %d.%d  " % (self.chapter, n)); set_run_font(r, 11, bold=True)
        self._runs(c, caption, size=11)
        t = self.doc.add_table(rows=1 + len(rows), cols=len(header))
        t.style = "Table Grid"
        t.alignment = WD_TABLE_ALIGNMENT.CENTER
        t.autofit = False
        for ri, row in enumerate([header] + rows):
            tr = t.rows[ri]
            no_split(tr)
            if ri == 0:
                repeat_header(tr)
            for ci, val in enumerate(row):
                cell = tr.cells[ci]
                cell.width = Inches(widths[ci])
                p = cell.paragraphs[0]
                p.paragraph_format.space_after = Pt(1)
                p.paragraph_format.space_before = Pt(1)
                p.paragraph_format.line_spacing = 1.0
                p.alignment = WD_ALIGN_PARAGRAPH.LEFT
                if ri < min(2, len(rows)) or ri == len(rows) - 1:
                    p.paragraph_format.keep_with_next = True
                self._runs(p, val, size=size, base_bold=(ri == 0 or (bold_first_col and ci == 0)))
                if ri == 0:
                    shade(cell)
        # column widths in the grid as well
        grid = t._tbl.tblGrid
        for ci, gc in enumerate(grid.findall(qn("w:gridCol"))):
            gc.set(qn("w:w"), str(int(widths[ci] * 1440)))
        spacer = self.doc.add_paragraph()
        spacer.paragraph_format.space_after = Pt(4)
        spacer.paragraph_format.line_spacing = 1.0
        return t

    # ---- cover and index ----
    def cover(self):
        def line(text, size, bold=False, before=0, after=0, italic=False):
            p = self.doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.space_before = Pt(before)
            p.paragraph_format.space_after = Pt(after)
            p.paragraph_format.line_spacing = 1.0
            r = p.add_run(text); set_run_font(r, size, bold=bold, italic=italic)
            return p
        line("SVKM's NMIMS", 14, True, before=6)
        line("Mukesh Patel School of Technology Management and Engineering, Mumbai", 13, False, after=70)
        line("NavCert Offline", 30, True, after=14)
        line("An Offline Android Application for Scanning and", 16, False)
        line("Reading Maritime Certificates", 16, False, after=50)
        rule = line("", 6, after=40)
        pbdr = OxmlElement("w:pBdr"); b = OxmlElement("w:bottom")
        b.set(qn("w:val"), "single"); b.set(qn("w:sz"), "8"); b.set(qn("w:space"), "1"); b.set(qn("w:color"), "000000")
        pbdr.append(b); rule._p.get_or_add_pPr().append(pbdr)
        rule.paragraph_format.left_indent = Inches(1.3); rule.paragraph_format.right_indent = Inches(1.3)
        line("Mini Project Report", 18, True, after=10)
        line("Mobile Application Development (702AI0E002)", 14, False, after=70)
        line("Submitted by: Aadidev Raizada", 14, True, after=6)
        line("Roll No.: N152", 13, False, after=6)
        line("B.Tech, Semester V", 13, False, after=70)
        line("SVKM's NMIMS Mukesh Patel School of Technology", 13, False)
        line("Management and Engineering, Mumbai", 13, False, after=16)
        line("Academic Year 2026-27", 13, True)
        self.page_break()

    def index(self, entries):
        p = self.doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_after = Pt(14)
        r = p.add_run("Index"); set_run_font(r, 18, bold=True)
        hdr = self.doc.add_paragraph()
        hdr.paragraph_format.tab_stops.add_tab_stop(Inches(TEXT_WIDTH_IN), WD_TAB_ALIGNMENT.RIGHT)
        hdr.paragraph_format.space_after = Pt(4)
        r = hdr.add_run("Chapter and section\tPage"); set_run_font(r, 11.5, bold=True)
        first = True
        paras = []
        for lvl, key, text in entries:
            ep = self.doc.add_paragraph(style="TOC %d" % lvl)
            if first:
                r1 = ep.add_run(); fc = OxmlElement("w:fldChar"); fc.set(qn("w:fldCharType"), "begin"); r1._element.append(fc)
                r2 = ep.add_run(); it = OxmlElement("w:instrText"); it.set(qn("xml:space"), "preserve")
                it.text = ' TOC \\o "1-2" \\h \\z \\u '; r2._element.append(it)
                r3 = ep.add_run(); fc = OxmlElement("w:fldChar"); fc.set(qn("w:fldCharType"), "separate"); r3._element.append(fc)
                first = False
            r = ep.add_run(text.replace("  ", " ") + "\t" + str(self.pages.get(key, 0)))
            set_run_font(r, 11.5 if lvl == 1 else 10.5, bold=(lvl == 1))
            paras.append(ep)
        rE = paras[-1].add_run(); fc = OxmlElement("w:fldChar"); fc.set(qn("w:fldCharType"), "end"); rE._element.append(fc)

    def references(self):
        p = self.doc.add_heading(level=1)
        r = p.add_run("References"); set_run_font(r, 16, bold=True)
        for i, k in enumerate(self.cites.order, 1):
            rp = self.doc.add_paragraph()
            rp.alignment = WD_ALIGN_PARAGRAPH.LEFT
            rp.paragraph_format.left_indent = Inches(0.45)
            rp.paragraph_format.first_line_indent = Inches(-0.45)
            rp.paragraph_format.space_after = Pt(3)
            rp.paragraph_format.line_spacing = 1.0
            rp.paragraph_format.tab_stops.add_tab_stop(Inches(0.45))
            r = rp.add_run("[%d]\t" % i); set_run_font(r, 10.5)
            self._runs(rp, REFS[k], size=10.5)

    def save(self, path):
        self.doc.save(path)

# --------------------------------------------------------------------------
# Content
# --------------------------------------------------------------------------

from content import write_body  # noqa: E402  (content.py sits next to this script)


def collect_headings():
    make_figures()
    probe = Report({})
    write_body(probe)
    return probe.headings + [(1, "References", "References")]


HEADINGS = collect_headings()


def build(pages):
    rep = Report(pages)
    rep.cover()
    rep.index(HEADINGS)
    write_body(rep)
    rep.references()
    got = [(l, k) for l, k, _ in rep.headings]
    want = [(l, k) for l, k, _ in HEADINGS if k != "References"]
    if got != want:
        raise SystemExit("HEADINGS list in content.py does not match the body:\n%s\n%s" % (got, want))
    rep.save(OUT_DOCX)
    return rep


def to_pdf():
    subprocess.run([SOFFICE, "--headless", "--convert-to", "pdf", "--outdir", HERE, OUT_DOCX],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


PAGE_SCRIPT = r'''
import sys, json, pymupdf
doc = pymupdf.open(sys.argv[1]); keys = json.loads(sys.argv[2])
norm = lambda s: " ".join(s.replace("\u00a0", " ").split())
found = {}
for i, page in enumerate(doc, 1):
    lines = [norm(l) for l in page.get_text("text").splitlines()]
    for k, text in keys:
        if norm(text) in lines:
            found[k] = i
print(json.dumps({"pages": found, "count": len(doc)}))
'''


def read_pages():
    keys = [[k, t] for _, k, t in HEADINGS]
    out = subprocess.run([VENV_PY, "-c", PAGE_SCRIPT, OUT_PDF, json.dumps(keys)],
                         check=True, capture_output=True, text=True).stdout
    return json.loads(out)


def main():
    pages = {}
    if os.path.exists(PAGES_JSON):
        pages = json.load(open(PAGES_JSON))
    for attempt in range(5):
        build(pages)
        to_pdf()
        res = read_pages()
        missing = [k for _, k, _ in HEADINGS if k not in res["pages"]]
        if missing:
            print("Headings not found in PDF:", missing)
        print("attempt %d: %d pages" % (attempt + 1, res["count"]))
        if res["pages"] == pages:
            print("Index page numbers match the PDF.")
            break
        pages = res["pages"]
        json.dump(pages, open(PAGES_JSON, "w"), indent=1)
    for lvl, k, _ in HEADINGS:
        if lvl == 1:
            print("  %-45s %s" % (k, pages.get(k)))


if __name__ == "__main__":
    sys.path.insert(0, HERE)
    main()
