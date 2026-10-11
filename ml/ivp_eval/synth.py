"""Synthetic certificate pages with known text, plus controlled camera degradations.

All content is generated: fictional organisations, people and numbers. Every page
and capture is a pure function of its seed, so the benchmark can be regenerated
instead of stored.
"""
from __future__ import annotations

import datetime as dt
import random
import textwrap
from dataclasses import dataclass, field

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

PAGE_W, PAGE_H = 1240, 1754          # A4 at 150 dpi
FRAME_W, FRAME_H = 1800, 2400        # camera frame (portrait 3:4)
FONT_DIR = "C:/Windows/Fonts/"
FONTS = [  # (regular, bold)
    ("times.ttf", "timesbd.ttf"), ("georgia.ttf", "georgiab.ttf"), ("arial.ttf", "arialbd.ttf"),
    ("calibri.ttf", "calibrib.ttf"), ("verdana.ttf", "verdanab.ttf"), ("pala.ttf", "palab.ttf"),
    ("BOOKOS.TTF", "BOOKOSB.TTF"), ("tahoma.ttf", "tahomabd.ttf"), ("trebuc.ttf", "trebucbd.ttf"),
    ("segoeui.ttf", "segoeuib.ttf"), ("cambriab.ttf", "cambriab.ttf"), ("GARA.TTF", "GARABD.TTF"),
]
CERT_NAMES = [
    "Basic Safety Training", "Advanced Fire Fighting", "Medical First Aid", "Proficiency in Survival Craft",
    "Ship Security Officer", "Security Awareness Training", "Oil Tanker Familiarisation",
    "GMDSS General Operator", "Elementary First Aid", "Personal Survival Techniques",
    "Bridge Resource Management", "Engine Room Resource Management", "Fast Rescue Boats",
    "Crowd Management Training", "High Voltage Safety", "Liquefied Gas Tanker Operations",
]
FIRST = ["Arjun", "Meera", "Rohan", "Priya", "Daniel", "Elena", "Kiran", "Sofia", "Vikram", "Ana",
         "Tomas", "Leila", "Nikhil", "Grace", "Omar", "Ishaan", "Clara", "Mateo", "Asha", "Lucas"]
LAST = ["Mehta", "Fernandes", "Rao", "Kowalski", "Santos", "Iyer", "Novak", "Pereira", "Kapoor",
        "Lindqvist", "Nair", "Okafor", "Bianchi", "Desai", "Haddad", "Larsen", "Joshi", "Moreau"]
PORTS = ["Mumbai", "Chennai", "Kolkata", "Kochi", "Visakhapatnam", "Singapore", "Rotterdam", "Piraeus",
         "Manila", "Dubai", "Hamburg", "Busan", "Goa", "Mangalore", "Kandla", "Valletta"]
AUTHORITIES = [
    "Westhaven Maritime Training Board", "Coastal Seafarer Licensing Office", "Harbour Institute of Nautical Studies",
    "Northbay Marine Safety Council", "Bluewater Academy of Seamanship", "Eastport Merchant Navy College",
    "Southern Cross Maritime Authority", "Seaview Institute of Marine Engineering",
]
SENTENCES = [
    "This is to certify that {holder} has successfully completed the approved course of training in {course}.",
    "The course was conducted at {inst} from {start} to {end} under the supervision of qualified instructors.",
    "The holder has demonstrated the required knowledge, understanding and proficiency in the subject.",
    "The training programme meets the standards of competence prescribed for seafarers serving on seagoing ships.",
    "Practical exercises were carried out in a controlled environment with all prescribed safety measures in place.",
    "This document remains valid only when it bears the signature of the authorised officer and the official seal.",
    "Any alteration or erasure made on this document renders it invalid for all purposes.",
    "The holder should present this document together with a valid identity document when requested.",
    "Assessment included written examination, oral questioning and observation of practical drills.",
    "Records of attendance and assessment are retained by the training centre for verification.",
]
MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October",
          "November", "December"]
FIELD_LABELS = {
    "certificate_name": "Certificate Name",
    "holder": "Name of Holder",
    "certificate_no": "Certificate No.",
    "issue_date": "Date of Issue",
    "port_of_issue": "Place of Issue",
    "issuing_authority": "Issuing Authority",
    "expiry_date": "Date of Expiry",
}
FIVE_FIELDS = ["certificate_name", "issue_date", "port_of_issue", "issuing_authority", "expiry_date"]


def fmt_date(d: dt.date, style: int) -> str:
    if style == 0:
        return f"{d.day:02d} {MONTHS[d.month - 1]} {d.year}"
    if style == 1:
        return f"{d.day:02d}/{d.month:02d}/{d.year}"
    return f"{d.day:02d}-{MONTHS[d.month - 1][:3].upper()}-{d.year}"


def font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT_DIR + name, size)


@dataclass
class Page:
    seed: int
    image: np.ndarray            # BGR, PAGE_H x PAGE_W
    lines: list[str]             # ground-truth text lines in reading order
    fields: dict[str, str]
    font: str
    ink_mask: np.ndarray = field(repr=False, default=None)  # True where printed text/marks are dark

    @property
    def text(self) -> str:
        return "\n".join(self.lines)


def render_page(seed: int) -> Page:
    rng = random.Random(seed)
    regular, bold = rng.choice(FONTS)
    ink = rng.choice([(20, 20, 20), (15, 25, 60), (35, 30, 30)])
    accent = rng.choice([(20, 50, 120), (110, 20, 30), (20, 80, 60), (60, 40, 100)])
    paper = tuple(int(v) for v in rng.choice([(255, 255, 255), (252, 250, 243), (248, 246, 238)]))
    img = Image.new("RGB", (PAGE_W, PAGE_H), paper)
    draw = ImageDraw.Draw(img)
    lines: list[str] = []

    # decorative double border (a realistic distractor for quadrilateral detection)
    m = 46
    draw.rectangle([m, m, PAGE_W - m, PAGE_H - m], outline=accent, width=5)
    draw.rectangle([m + 12, m + 12, PAGE_W - m - 12, PAGE_H - m - 12], outline=accent, width=2)

    authority = rng.choice(AUTHORITIES)
    course = rng.choice(CERT_NAMES)
    holder = f"{rng.choice(FIRST)} {rng.choice(LAST)}"
    port = rng.choice(PORTS)
    issue = dt.date(2019, 1, 1) + dt.timedelta(days=rng.randrange(0, 6 * 365))
    expiry = issue.replace(year=issue.year + 5) if not (issue.month == 2 and issue.day == 29) else issue + dt.timedelta(days=1826)
    start = issue - dt.timedelta(days=rng.randrange(8, 30))
    end = start + dt.timedelta(days=rng.randrange(2, 6))
    style = rng.randrange(3)
    abbrev = "".join(w[0] for w in authority.split() if w[0].isupper())
    cert_no = f"{abbrev}/{issue.year}/{rng.randrange(10000, 99999)}"
    fields = {
        "certificate_name": course, "holder": holder, "certificate_no": cert_no,
        "issue_date": fmt_date(issue, style), "port_of_issue": port,
        "issuing_authority": authority, "expiry_date": fmt_date(expiry, style),
    }

    def centered(text: str, y: int, f: ImageFont.FreeTypeFont, fill) -> int:
        w = draw.textlength(text, font=f)
        draw.text(((PAGE_W - w) / 2, y), text, font=f, fill=fill)
        lines.append(text)
        return y + int(f.size * 1.45)

    y = 120
    y = centered(authority.upper(), y, font(bold, 36), accent)
    y = centered(f"Training Centre, {port}", y, font(regular, 26), ink) + 30
    y = centered("CERTIFICATE OF PROFICIENCY", y, font(bold, 46), ink)
    y = centered(course, y, font(bold, 38), accent) + 30

    body_f = font(regular, rng.choice([25, 26, 27]))
    text_w = PAGE_W - 2 * 130
    picks = [0, 1] + rng.sample(range(2, len(SENTENCES)), 3)
    paragraph = " ".join(SENTENCES[i] for i in picks).format(
        holder=holder, course=course, inst=f"the {authority} training centre",
        start=fmt_date(start, style), end=fmt_date(end, style))
    avg = body_f.getlength("abcdefghijklmnopqrstuvwxyz") / 26
    for line in textwrap.wrap(paragraph, width=int(text_w / avg)):
        while draw.textlength(line, font=body_f) > text_w and len(line) > 10:
            line = line[:-1]  # never triggers in practice; guards against overflow
        draw.text((130, y), line, font=body_f, fill=ink)
        lines.append(line)
        y += int(body_f.size * 1.5)
    y += 40

    label_f, value_f = font(bold, 25), font(regular, 25)
    for key in ["certificate_name", "holder", "certificate_no", "issue_date", "port_of_issue",
                "issuing_authority", "expiry_date"]:
        label = FIELD_LABELS[key] + ":"
        draw.text((130, y), label, font=label_f, fill=ink)
        draw.text((470, y), fields[key], font=value_f, fill=ink)
        lines.append(f"{label} {fields[key]}")
        y += 46

    # signature: smooth random stroke in blue ink
    sig_y = max(y + 90, PAGE_H - 330)
    pts = []
    x0 = 150
    for k in range(120):
        t = k / 119
        pts.append((x0 + 300 * t + 18 * np.sin(t * rng.uniform(14, 22)),
                    sig_y + 28 * np.sin(t * rng.uniform(6, 11) + 1.0) + 10 * np.cos(t * 31)))
    draw.line(pts, fill=(25, 45, 140), width=4, joint="curve")
    draw.line([(130, sig_y + 60), (500, sig_y + 60)], fill=ink, width=2)
    draw.text((130, sig_y + 70), "Authorised Signatory", font=value_f, fill=ink)
    lines.append("Authorised Signatory")
    footer_f = font(regular, 20)
    footer = "Synthetic test document generated for image processing evaluation."
    draw.text((130, PAGE_H - 140), footer, font=footer_f, fill=ink)
    lines.append(footer)

    ink_mask = np.array(img.convert("L")) < 128
    # coloured seal (semi-transparent rings, no text) overlapping the signature area
    seal = Image.new("RGBA", (PAGE_W, PAGE_H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(seal)
    cx, cy, r = rng.randrange(820, 980), sig_y + 10, rng.randrange(95, 120)
    col = rng.choice([(200, 30, 40), (40, 60, 190), (120, 40, 140)])
    for rr, wdt in [(r, 6), (r - 14, 3), (r - 50, 3)]:
        sd.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], outline=col + (150,), width=wdt)
    for a in range(0, 360, 30):  # anchor-like star emblem
        ang = np.deg2rad(a)
        sd.line([(cx, cy), (cx + (r - 58) * np.cos(ang), cy + (r - 58) * np.sin(ang))], fill=col + (150,), width=3)
    img = Image.alpha_composite(img.convert("RGBA"), seal).convert("RGB")
    bgr = cv2.cvtColor(np.array(img), cv2.COLOR_RGB2BGR)
    return Page(seed, bgr, lines, fields, regular, ink_mask)


# ---------------------------------------------------------------- camera simulation
DEGRADATIONS = {  # severity 1, 2, 3
    "tilt": [0.06, 0.10, 0.14],            # max corner jitter as a fraction of frame width
    "shadow": [0.65, 0.45, 0.30],          # darkest illumination factor
    "low_light": [0.40, 0.25, 0.15],       # exposure gain
    "overexposure": [1.45, 1.80, 2.20],    # exposure gain (with clipping)
    "gaussian_blur": [1.2, 2.0, 3.0],      # sigma in frame pixels
    "motion_blur": [7, 13, 19],            # kernel length in frame pixels
    "noise": [10.0, 20.0, 32.0],           # Gaussian sensor noise sigma (8-bit levels)
    "jpeg": [25, 12, 6],                   # JPEG quality
}
BASE = {"jitter": 0.03, "noise": 2.0, "jpeg": 92}


@dataclass
class Capture:
    image: np.ndarray       # BGR FRAME_H x FRAME_W
    quad: np.ndarray        # true page corners TL, TR, BR, BL in frame pixels
    degradation: str
    severity: int


def background(rng: np.random.Generator) -> np.ndarray:
    kind = rng.integers(3)
    yy, xx = np.mgrid[0:FRAME_H, 0:FRAME_W].astype(np.float32)
    low = cv2.resize(rng.normal(0, 1, (12, 9)).astype(np.float32), (FRAME_W, FRAME_H), interpolation=cv2.INTER_CUBIC)
    if kind == 0:   # wood desk
        grain = np.sin((xx + 40 * low) / rng.uniform(9, 16)) * 12
        base = np.array([60, 95, 140], np.float32)
        img = base + (grain + 10 * low)[..., None]
    elif kind == 1:  # grey laminate
        base = np.array([95, 98, 100], np.float32)
        img = base + (14 * low)[..., None] + rng.normal(0, 4, (FRAME_H, FRAME_W, 1)).astype(np.float32)
    else:            # dark fabric
        base = np.array([80, 52, 38], np.float32)
        weave = (np.sin(xx / 3.0) * np.sin(yy / 3.0)) * 6
        img = base + (weave + 10 * low)[..., None]
    img = np.clip(img, 0, 255).astype(np.uint8)
    # clutter: a pen and a coaster partially in frame
    color = tuple(int(c) for c in rng.integers(20, 200, 3))
    x = int(rng.integers(50, FRAME_W - 50))
    cv2.line(img, (x, 0), (x + int(rng.integers(-200, 200)), int(rng.integers(150, 300))), color, 22)
    cv2.circle(img, (int(rng.choice([0, FRAME_W])), int(rng.integers(1800, FRAME_H))), 180,
               tuple(int(c) for c in rng.integers(40, 220, 3)), -1)
    return img


def random_quad(rng: np.random.Generator, jitter: float) -> np.ndarray:
    scale = rng.uniform(0.80, 0.88) * FRAME_H / PAGE_H
    w, h = PAGE_W * scale, PAGE_H * scale
    cx, cy = FRAME_W / 2 + rng.uniform(-40, 40), FRAME_H / 2 + rng.uniform(-40, 40)
    ang = np.deg2rad(rng.uniform(-1, 1) * jitter * 100)  # up to 3/6/10/14 degrees
    rect = np.array([[-w / 2, -h / 2], [w / 2, -h / 2], [w / 2, h / 2], [-w / 2, h / 2]])
    rot = np.array([[np.cos(ang), -np.sin(ang)], [np.sin(ang), np.cos(ang)]])
    quad = rect @ rot.T + [cx, cy]
    quad += rng.uniform(-1, 1, (4, 2)) * jitter * FRAME_W
    # keep inside frame with a small margin, shrinking towards the centre if needed
    for _ in range(20):
        if quad[:, 0].min() > 15 and quad[:, 1].min() > 15 and quad[:, 0].max() < FRAME_W - 15 and quad[:, 1].max() < FRAME_H - 15:
            break
        quad = (quad - [cx, cy]) * 0.97 + [cx, cy]
    return quad


def illumination_field(rng: np.random.Generator, darkest: float) -> np.ndarray:
    yy, xx = np.mgrid[0:FRAME_H, 0:FRAME_W].astype(np.float32)
    ang = rng.uniform(0, 2 * np.pi)
    g = (np.cos(ang) * xx / FRAME_W + np.sin(ang) * yy / FRAME_H)
    g = (g - g.min()) / (g.max() - g.min())
    field_ = 1.0 - (1.0 - (1 + darkest) / 2) * g          # smooth gradient to the midpoint
    mask = np.zeros((FRAME_H, FRAME_W), np.float32)         # cast shadow (e.g. phone/hand)
    p0 = rng.uniform([0, 0], [FRAME_W, FRAME_H * 0.5])
    poly = np.array([p0, p0 + [FRAME_W * 0.9, rng.uniform(-200, 200)],
                     [FRAME_W * 1.2, FRAME_H * 1.2], [-FRAME_W * 0.2, FRAME_H * 1.2]], np.int32)
    if rng.random() < 0.5:
        poly[:, 0] = FRAME_W - poly[:, 0]
    cv2.fillPoly(mask, [poly], 1.0)
    mask = cv2.GaussianBlur(mask, (0, 0), 35)
    return field_ * (1 - mask * (1 - darkest))


def motion_kernel(length: int, angle_deg: float) -> np.ndarray:
    k = np.zeros((length, length), np.float32)
    k[length // 2, :] = 1.0
    rot = cv2.getRotationMatrix2D((length / 2 - 0.5, length / 2 - 0.5), angle_deg, 1.0)
    k = cv2.warpAffine(k, rot, (length, length))
    return k / k.sum()


def capture(page: Page, degradation: str, severity: int, seed: int) -> Capture:
    rng = np.random.default_rng(seed)
    value = DEGRADATIONS[degradation][severity - 1] if degradation != "clean" else None
    jitter = value if degradation == "tilt" else BASE["jitter"]
    quad = random_quad(rng, jitter)
    frame = background(rng).astype(np.float32)
    src = np.array([[0, 0], [PAGE_W - 1, 0], [PAGE_W - 1, PAGE_H - 1], [0, PAGE_H - 1]], np.float32)
    H = cv2.getPerspectiveTransform(src, quad.astype(np.float32))
    warped = cv2.warpPerspective(page.image, H, (FRAME_W, FRAME_H), flags=cv2.INTER_AREA)
    alpha = cv2.warpPerspective(np.ones((PAGE_H, PAGE_W), np.float32), H, (FRAME_W, FRAME_H), flags=cv2.INTER_LINEAR)
    # soft drop shadow under the page edge so the page/background boundary looks photographic
    frame = frame * (1 - 0.25 * cv2.GaussianBlur(alpha, (0, 0), 12)[..., None])
    frame = frame * (1 - alpha[..., None]) + warped.astype(np.float32) * alpha[..., None]

    # mild natural vignetting/light gradient in every capture
    yy, xx = np.mgrid[0:FRAME_H, 0:FRAME_W].astype(np.float32)
    r2 = ((xx - FRAME_W / 2) / FRAME_W) ** 2 + ((yy - FRAME_H / 2) / FRAME_H) ** 2
    frame *= (1 - 0.18 * r2)[..., None]

    noise_sigma = BASE["noise"]
    if degradation == "shadow":
        frame *= illumination_field(rng, value)[..., None]
    elif degradation == "low_light":
        frame = 255.0 * (np.clip(frame * value, 0, 255) / 255.0) ** 1.1
        noise_sigma = 4.0 + 2.0 * severity          # low light raises relative sensor noise
    elif degradation == "overexposure":
        frame = frame * value + 25 * (severity)
    elif degradation == "gaussian_blur":
        frame = cv2.GaussianBlur(frame, (0, 0), value)
    elif degradation == "motion_blur":
        frame = cv2.filter2D(frame, -1, motion_kernel(int(value), rng.uniform(0, 180)))
    elif degradation == "noise":
        noise_sigma = value
    frame = frame + rng.normal(0, noise_sigma, frame.shape).astype(np.float32)
    frame = np.clip(frame, 0, 255).astype(np.uint8)
    quality = int(value) if degradation == "jpeg" else BASE["jpeg"]
    ok, enc = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, quality])
    frame = cv2.imdecode(enc, cv2.IMREAD_COLOR)
    return Capture(frame, quad, degradation, severity)


def warp_to_page(frame: np.ndarray, quad: np.ndarray) -> np.ndarray:
    """Oracle rectification with the true corners to the exact page raster (for pixel metrics)."""
    dst = np.array([[0, 0], [PAGE_W - 1, 0], [PAGE_W - 1, PAGE_H - 1], [0, PAGE_H - 1]], np.float32)
    H = cv2.getPerspectiveTransform(quad.astype(np.float32), dst)
    return cv2.warpPerspective(frame, H, (PAGE_W, PAGE_H), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)
