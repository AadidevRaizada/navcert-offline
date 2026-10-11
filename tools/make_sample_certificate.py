"""Generate the synthetic sample certificate bundled with the Pipeline Lab.

Every name, vessel, number and authority on this page is invented. The script draws a flat
certificate and then a photographed-looking version (perspective, uneven light, shadow, slight
blur and sensor noise) so the Lab and the emulator demo work without a camera or private data.

Usage: ml/.venv/Scripts/python.exe tools/make_sample_certificate.py
"""
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "assets" / "lab"
FONTS = Path("C:/Windows/Fonts")


def font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / name), size)


def centered(draw: ImageDraw.ImageDraw, y: int, text: str, fnt, fill, width: int) -> None:
    w = draw.textlength(text, font=fnt)
    draw.text(((width - w) / 2, y), text, font=fnt, fill=fill)


def draw_flat() -> Image.Image:
    w, h = 1240, 1754
    img = Image.new("RGB", (w, h), (250, 247, 238))
    d = ImageDraw.Draw(img)
    navy = (22, 38, 92)
    ink = (28, 28, 30)

    # Double border.
    d.rectangle([40, 40, w - 40, h - 40], outline=navy, width=10)
    d.rectangle([62, 62, w - 62, h - 62], outline=navy, width=3)

    # Simple emblem: anchor in a circle.
    cx, cy = w // 2, 210
    d.ellipse([cx - 80, cy - 80, cx + 80, cy + 80], outline=navy, width=6)
    d.line([cx, cy - 55, cx, cy + 50], fill=navy, width=8)
    d.line([cx - 35, cy - 30, cx + 35, cy - 30], fill=navy, width=7)
    d.arc([cx - 55, cy - 10, cx + 55, cy + 60], 20, 160, fill=navy, width=8)
    d.ellipse([cx - 12, cy - 72, cx + 12, cy - 48], outline=navy, width=5)

    centered(d, 320, "MARITIME SAFETY BOARD OF NORTH ESTRAVIA", font("arialbd.ttf", 34), navy, w)
    centered(d, 372, "Issued under the provisions of the STCW Convention", font("ariali.ttf", 26), ink, w)
    centered(d, 450, "CERTIFICATE OF PROFICIENCY", font("arialbd.ttf", 62), ink, w)
    centered(d, 535, "Proficiency in Survival Craft and Rescue Boats", font("arial.ttf", 34), ink, w)
    d.line([160, 600, w - 160, 600], fill=navy, width=3)

    label = font("arialbd.ttf", 30)
    value = font("arial.ttf", 32)
    rows = [
        ("Certificate No.", "NE-SCRB-2025-04817"),
        ("Name of holder", "ARJUN MEHRA KALEVI"),
        ("Date of birth", "14 March 1996"),
        ("Rank / Capacity", "Second Officer"),
        ("Vessel", "MV CORAL MERIDIAN (IMO 9876543)"),
        ("Date of issue", "18 June 2025"),
        ("Place of issue", "Port of Valmora"),
        ("Issuing authority", "Maritime Safety Board, Valmora"),
        ("Date of expiry", "17 June 2030"),
    ]
    y = 650
    for k, v in rows:
        d.text((150, y), k, font=label, fill=navy)
        d.text((520, y), v, font=value, fill=ink)
        d.line([520, y + 44, w - 150, y + 44], fill=(190, 186, 176), width=1)
        y += 74

    body = font("arial.ttf", 25)
    text = [
        "This is to certify that the holder has completed approved training and",
        "has been found competent in accordance with Regulation VI/2 and",
        "Section A-VI/2 of the STCW Code, as amended.",
    ]
    y += 24
    for line in text:
        d.text((150, y), line, font=body, fill=ink)
        y += 38

    # Signature (hand-drawn curve) and line.
    sig_y = 1500
    pts = [(170 + t * 3.2, sig_y - 30 + 22 * np.sin(t / 9.0) * np.cos(t / 23.0)) for t in range(0, 90)]
    d.line(pts, fill=(20, 40, 140), width=4)
    d.line([150, sig_y + 20, 520, sig_y + 20], fill=ink, width=2)
    d.text((150, sig_y + 32), "Registrar of Seafarers", font=font("arial.ttf", 24), fill=ink)

    # Red circular stamp with a translucent overlay.
    stamp = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    s = ImageDraw.Draw(stamp)
    sx, sy, r = 930, 1450, 135
    red = (196, 30, 42, 190)
    s.ellipse([sx - r, sy - r, sx + r, sy + r], outline=red, width=9)
    s.ellipse([sx - r + 22, sy - r + 22, sx + r - 22, sy + r - 22], outline=red, width=4)
    sf = font("arialbd.ttf", 30)
    for i, word in enumerate(["MSB", "VALMORA", "OFFICIAL"]):
        tw = s.textlength(word, font=sf)
        s.text((sx - tw / 2, sy - 58 + i * 40), word, font=sf, fill=red)
    stamp = stamp.rotate(-12, center=(sx, sy), resample=Image.BICUBIC)
    img = Image.alpha_composite(img.convert("RGBA"), stamp).convert("RGB")
    return img


def photograph(flat: Image.Image) -> np.ndarray:
    rng = np.random.default_rng(152)
    page = cv2.cvtColor(np.array(flat), cv2.COLOR_RGB2BGR)
    ph, pw = page.shape[:2]
    W, H = 1500, 2000

    # Wooden-desk style background: vertical gradient with grain.
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    grain = 10 * np.sin(xx / 7.0 + 3 * np.sin(yy / 90.0)) + rng.normal(0, 4, (H, W))
    base = np.stack([60 + 0.01 * yy + grain, 88 + 0.012 * yy + grain, 120 + 0.012 * yy + grain], axis=-1)
    bg = np.clip(base, 0, 255).astype(np.uint8)

    src = np.float32([[0, 0], [pw - 1, 0], [pw - 1, ph - 1], [0, ph - 1]])
    dst = np.float32([[230, 190], [1290, 250], [1360, 1800], [140, 1740]])
    M = cv2.getPerspectiveTransform(src, dst)
    warped = cv2.warpPerspective(page, M, (W, H), flags=cv2.INTER_CUBIC)
    mask = cv2.warpPerspective(np.full((ph, pw), 255, np.uint8), M, (W, H))

    # Soft drop shadow under the page.
    shadow = cv2.GaussianBlur(cv2.warpPerspective(np.full((ph, pw), 255, np.uint8),
                                                  M @ np.float32([[1, 0, 0], [0, 1, 0], [0, 0, 1]]), (W, H)), (0, 0), 25)
    shadow = np.roll(np.roll(shadow, 18, axis=0), 14, axis=1).astype(np.float32) / 255.0
    out = bg.astype(np.float32) * (1 - 0.45 * shadow[..., None])
    m = mask.astype(np.float32)[..., None] / 255.0
    out = out * (1 - m) + warped.astype(np.float32) * m

    # Uneven illumination: brighter top-left, a phone-shaped shadow lower right.
    light = 0.82 + 0.25 * (1 - (xx / W + yy / H) / 2)
    hand = np.zeros((H, W), np.float32)
    cv2.ellipse(hand, (1180, 1620), (420, 260), -25, 0, 360, 1.0, -1)
    hand = cv2.GaussianBlur(hand, (0, 0), 70)
    light = light * (1 - 0.33 * hand)
    out = out * light[..., None]

    out = np.clip(out, 0, 255).astype(np.uint8)
    out = cv2.GaussianBlur(out, (0, 0), 1.1)
    out = np.clip(out.astype(np.float32) + rng.normal(0, 3.0, out.shape), 0, 255).astype(np.uint8)
    return out


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    flat = draw_flat()
    flat.save(OUT / "sample_certificate_flat.png", optimize=True)
    photo = photograph(flat)
    cv2.imwrite(str(OUT / "sample_certificate_photo.jpg"), photo, [cv2.IMWRITE_JPEG_QUALITY, 90])
    print("wrote", OUT)


if __name__ == "__main__":
    main()
