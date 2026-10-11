"""OCR engine wrapper (PP-OCRv6 small via PaddleOCR + ONNX Runtime, CPU) and text metrics."""
from __future__ import annotations

import hashlib
import json
import os
import re
from pathlib import Path

import cv2
import numpy as np
from rapidfuzz.distance import Levenshtein

os.environ.setdefault("PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK", "True")
CACHE = Path(__file__).resolve().parent / "cache"
ENGINE = {
    "library": "paddleocr", "engine": "onnxruntime", "device": "cpu",
    "text_detection_model_name": "PP-OCRv6_small_det",
    "text_recognition_model_name": "PP-OCRv6_small_rec",
    "use_doc_orientation_classify": False, "use_doc_unwarping": False, "use_textline_orientation": False,
}
_ocr = None


def _engine():
    global _ocr
    if _ocr is None:
        from paddleocr import PaddleOCR
        kwargs = {k: v for k, v in ENGINE.items() if k != "library"}
        _ocr = PaddleOCR(**kwargs)
    return _ocr


def engine_versions() -> dict:
    import onnxruntime
    import paddleocr
    return {"paddleocr": paddleocr.__version__, "onnxruntime": onnxruntime.__version__, "opencv": cv2.__version__,
            **ENGINE}


def run_ocr(image: np.ndarray) -> list[dict]:
    """Returns [{text, score, box:[x1,y1,x2,y2]}]; cached by image content hash."""
    img = image if image.ndim == 3 else cv2.cvtColor(image, cv2.COLOR_GRAY2BGR)
    key = hashlib.sha1(np.ascontiguousarray(img).tobytes() + str(img.shape).encode()).hexdigest()
    path = CACHE / f"{key}.json"
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    res = _engine().predict(img)[0].json["res"]
    out = [{"text": t, "score": float(s), "box": [int(v) for v in b]}
           for t, s, b in zip(res["rec_texts"], res["rec_scores"], res["rec_boxes"]) if t.strip()]
    CACHE.mkdir(exist_ok=True)
    path.write_text(json.dumps(out, ensure_ascii=False), encoding="utf-8")
    return out


def reconstruct_lines(items: list[dict]) -> list[str]:
    """Group recognised boxes into text lines by vertical overlap, then read left to right."""
    boxes = sorted(items, key=lambda it: (it["box"][1] + it["box"][3]) / 2)
    lines: list[list[dict]] = []
    for it in boxes:
        x1, y1, x2, y2 = it["box"]
        yc, h = (y1 + y2) / 2, max(1, y2 - y1)
        if lines:
            last = lines[-1]
            ly = np.mean([(b["box"][1] + b["box"][3]) / 2 for b in last])
            lh = np.mean([b["box"][3] - b["box"][1] for b in last])
            if abs(yc - ly) < 0.5 * min(h, lh):
                last.append(it)
                continue
        lines.append([it])
    return [" ".join(b["text"].strip() for b in sorted(line, key=lambda b: b["box"][0])) for line in lines]


def normalise(text: str) -> str:
    """Lower-case, unify quotes/dashes, collapse all whitespace (incl. line breaks) to single spaces."""
    text = text.lower().replace("’", "'").replace("‘", "'").replace("–", "-").replace("—", "-")
    return re.sub(r"\s+", " ", text).strip()


def cer(ref: str, hyp: str) -> float:
    r, h = normalise(ref), normalise(hyp)
    return Levenshtein.distance(r, h) / max(1, len(r))


def wer(ref: str, hyp: str) -> float:
    r, h = normalise(ref).split(), normalise(hyp).split()
    return Levenshtein.distance(r, h) / max(1, len(r))


def word_recall(ref: str, hyp: str) -> float:
    """Order-free: fraction of reference words (multiset, punctuation stripped) present in the OCR output."""
    from collections import Counter
    tok = lambda t: Counter(w for w in (re.sub(r"[^a-z0-9]", "", x) for x in normalise(t).split()) if w)
    r, h = tok(ref), tok(hyp)
    return sum((r & h).values()) / max(1, sum(r.values()))


def _key(s: str) -> str:
    return re.sub(r"[^a-z0-9]", "", s.lower())


def extract_fields(lines: list[str], labels: dict[str, str]) -> dict[str, str | None]:
    """Simple label rule: first OCR line containing the label (ignoring case, spaces and punctuation);
    the value is the rest of that line after the label."""
    found = {}
    for field, label in labels.items():
        lab = _key(label)
        value = None
        for line in lines:
            k = _key(line)
            pos = k.find(lab)
            if pos >= 0:
                value = k[pos + len(lab):]
                break
        found[field] = value
    return found


def field_exact(found: dict[str, str | None], truth: dict[str, str]) -> dict[str, bool]:
    return {f: (found.get(f) is not None and found[f] == _key(truth[f])) for f in found}
