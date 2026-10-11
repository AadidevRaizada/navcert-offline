"""Python port of app/src/main/java/com/MAD/n152/DocumentScanner.kt.

Every constant and every OpenCV call mirrors the Kotlin implementation so the
measurements in this folder describe the preprocessing the Android app runs.
Images are BGR uint8 arrays (OpenCV convention); the app works on RGBA bitmaps,
and the grayscale conversion uses the same ITU-R BT.601 weights in both cases.
"""
from __future__ import annotations

from dataclasses import dataclass

import cv2
import numpy as np

MAX_ANALYSIS_DIMENSION = 1600
MAX_OCR_DIMENSION = 3200
MIN_DOCUMENT_AREA_RATIO = 0.15
CANNY_LOW, CANNY_HIGH = 55.0, 170.0
CLOSE_KERNEL = (5, 5)
DARK_THRESHOLD, BRIGHT_THRESHOLD, BLUR_THRESHOLD = 55.0, 220.0, 65.0
CLAHE_CLIP, CLAHE_TILES = 2.2, (8, 8)
MEDIAN_KSIZE = 3
ADAPTIVE_BLOCK, ADAPTIVE_C = 35, 11.0
UNSHARP = {"AUTO": (1.05, 1.32), "CLEAN": (1.35, 1.50)}
BORDER_PX = 18


@dataclass
class Quality:
    sharpness: float
    brightness: float
    label: str
    is_good: bool


def scale_to_maximum(image: np.ndarray, maximum: int) -> np.ndarray:
    """Bitmap.createScaledBitmap(filter=true) equivalent: bilinear, truncated sizes."""
    h, w = image.shape[:2]
    largest = max(w, h)
    if largest <= maximum:
        return image
    scale = maximum / largest
    size = (max(1, int(w * scale)), max(1, int(h * scale)))
    return cv2.resize(image, size, interpolation=cv2.INTER_LINEAR)


def measure_quality(bgr: np.ndarray) -> Quality:
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    lap = cv2.Laplacian(gray, cv2.CV_64F)
    sharpness = float(lap.std() ** 2)
    brightness = float(gray.mean())
    if brightness < DARK_THRESHOLD:
        return Quality(sharpness, brightness, "Too dark", False)
    if brightness > BRIGHT_THRESHOLD:
        return Quality(sharpness, brightness, "Too bright", False)
    if sharpness < BLUR_THRESHOLD:
        return Quality(sharpness, brightness, "Soft image", False)
    return Quality(sharpness, brightness, "Image looks good", True)


def order_corners(points: np.ndarray) -> np.ndarray:
    pts = np.asarray(points, dtype=np.float64).reshape(-1, 2)
    s = pts[:, 0] + pts[:, 1]
    d = pts[:, 1] - pts[:, 0]
    return np.array([pts[np.argmin(s)], pts[np.argmin(d)], pts[np.argmax(s)], pts[np.argmax(d)]])


def polygon_area(points: np.ndarray) -> float:
    p = np.asarray(points, dtype=np.float64)
    q = np.roll(p, -1, axis=0)
    return abs(float(np.sum(p[:, 0] * q[:, 1] - q[:, 0] * p[:, 1]))) / 2.0


def edge_map(bgr: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    blurred = cv2.GaussianBlur(gray, (5, 5), 0)
    edges = cv2.Canny(blurred, CANNY_LOW, CANNY_HIGH)
    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, CLOSE_KERNEL)
    closed = cv2.morphologyEx(edges, cv2.MORPH_CLOSE, kernel)
    return edges, closed


def detect_corners(bgr: np.ndarray) -> np.ndarray | None:
    _, closed = edge_map(bgr)
    contours, _ = cv2.findContours(closed, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    image_area = float(bgr.shape[0] * bgr.shape[1])
    candidates = []
    for contour in sorted(contours, key=cv2.contourArea, reverse=True)[:20]:
        if cv2.contourArea(contour) < image_area * MIN_DOCUMENT_AREA_RATIO:
            continue
        perimeter = cv2.arcLength(contour.astype(np.float32), True)
        approx = cv2.approxPolyDP(contour.astype(np.float32), perimeter * 0.02, True)
        if len(approx) != 4:
            continue
        if not cv2.isContourConvex(approx.astype(np.int32)):
            continue
        candidates.append(order_corners(approx.reshape(-1, 2)))
    if not candidates:
        return None
    return max(candidates, key=polygon_area)


def warp_perspective(source: np.ndarray, corners: np.ndarray) -> np.ndarray:
    c = np.asarray(corners, dtype=np.float64)
    dist = lambda a, b: float(np.hypot(*(c[a] - c[b])))
    out_w = max(1, int(max(dist(0, 1), dist(3, 2))))
    out_h = max(1, int(max(dist(0, 3), dist(1, 2))))
    dst = np.array([[0, 0], [out_w - 1, 0], [out_w - 1, out_h - 1], [0, out_h - 1]], dtype=np.float32)
    transform = cv2.getPerspectiveTransform(c.astype(np.float32), dst)
    return cv2.warpPerspective(source, transform, (out_w, out_h), flags=cv2.INTER_CUBIC,
                               borderMode=cv2.BORDER_REPLICATE)


def clahe_median(bgr_or_gray: np.ndarray) -> np.ndarray:
    gray = bgr_or_gray if bgr_or_gray.ndim == 2 else cv2.cvtColor(bgr_or_gray, cv2.COLOR_BGR2GRAY)
    contrast = cv2.createCLAHE(CLAHE_CLIP, CLAHE_TILES).apply(gray)
    return cv2.medianBlur(contrast, MEDIAN_KSIZE)


def add_border(gray: np.ndarray) -> np.ndarray:
    return cv2.copyMakeBorder(gray, BORDER_PX, BORDER_PX, BORDER_PX, BORDER_PX, cv2.BORDER_CONSTANT, value=255)


def enhance_for_ocr(source: np.ndarray, mode: str = "AUTO") -> np.ndarray:
    """Returns a single-channel image (the app converts it back to RGBA; content is identical)."""
    denoised = clahe_median(source)
    if mode == "BLACK_WHITE":
        out = cv2.adaptiveThreshold(denoised, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY,
                                    ADAPTIVE_BLOCK, ADAPTIVE_C)
    else:
        sigma, amount = UNSHARP[mode]
        blurred = cv2.GaussianBlur(denoised, (0, 0), sigma)
        out = cv2.addWeighted(denoised, amount, blurred, 1.0 - amount, 0.0)
    return add_border(out)


@dataclass
class ScanResult:
    normalized: np.ndarray
    analysis: np.ndarray
    quality: Quality
    corners_analysis: np.ndarray | None
    corners_full: np.ndarray | None
    rectified: np.ndarray


def prepare(image: np.ndarray, detect_document: bool = True) -> ScanResult:
    """DocumentScanner.prepare() up to (not including) enhancement."""
    normalized = scale_to_maximum(image, MAX_OCR_DIMENSION)
    analysis = scale_to_maximum(normalized, MAX_ANALYSIS_DIMENSION)
    quality = measure_quality(analysis)
    corners = detect_corners(analysis) if detect_document else None
    if corners is None:
        return ScanResult(normalized, analysis, quality, None, None, normalized.copy())
    sx = normalized.shape[1] / analysis.shape[1]
    sy = normalized.shape[0] / analysis.shape[0]
    full = corners * np.array([sx, sy])
    return ScanResult(normalized, analysis, quality, corners, full, warp_perspective(normalized, full))
