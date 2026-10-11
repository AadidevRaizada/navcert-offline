"""Capture-quality decision: the app's hand-built decision tree vs a learned CART tree.

The app (DocumentScanner.measureQuality) applies three ordered tests on the analysis image:
    mean gray < 55 -> "Too dark";  mean gray > 220 -> "Too bright";
    Laplacian variance < 65 -> "Soft image";  otherwise -> "Image looks good".
That is a depth-3 decision tree written by hand. Here we render synthetic pages,
degrade them in known ways (so the label is known from the generator), compute the same
two features at the same 1600 px analysis size, and learn a CART tree (Gini impurity,
depth <= 3) from a training split. Both trees are scored on a held-out test split.

Everything is synthetic: random glyph strings drawn with OpenCV. No certificate images.
"""

from __future__ import annotations

import random
from collections import Counter

import cv2
import numpy as np

CLASSES = ["good", "dark", "bright", "blurry"]
FEATURES = ["mean_gray", "laplacian_var"]


def render_page(rng: random.Random) -> np.ndarray:
    h, w = 1600, 1131  # A4 aspect at the app's 1600 px analysis limit
    paper = rng.randint(150, 215)  # phone photos of white paper rarely read as pure white
    img = np.full((h, w), paper, np.uint8)
    y = rng.randint(80, 160)
    letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789/:-"
    while y < h - 80:
        scale = rng.uniform(0.7, 1.4)
        text = "".join(rng.choice(letters + "  ") for _ in range(rng.randint(10, 40)))
        cv2.putText(img, text, (rng.randint(60, 140), y), cv2.FONT_HERSHEY_SIMPLEX, scale,
                    rng.randint(10, 60), rng.choice([1, 2]), cv2.LINE_AA)
        y += int(42 * scale) + rng.randint(4, 30)
    return img


def degrade(img: np.ndarray, label: str, rng: random.Random, nrng: np.random.Generator) -> np.ndarray:
    f = img.astype(np.float32)
    if label == "dark":
        f = f * rng.uniform(0.12, 0.30)
    elif label == "bright":
        f = 255 - (255 - f) * rng.uniform(0.05, 0.25)        # washed out by glare / overexposure
    elif label == "blurry":
        k = rng.uniform(2.5, 6.0)
        f = cv2.GaussianBlur(f, (0, 0), k)
    else:
        f = f * rng.uniform(0.85, 1.1)
        if rng.random() < 0.5:
            f = cv2.GaussianBlur(f, (0, 0), rng.uniform(0.3, 0.9))  # normal mild softness
    f = f + nrng.normal(0, rng.uniform(1.0, 4.0), f.shape)          # sensor noise
    return np.clip(f, 0, 255).astype(np.uint8)


def features(gray: np.ndarray) -> tuple[float, float]:
    lap = cv2.Laplacian(gray, cv2.CV_64F)
    return float(gray.mean()), float(lap.std() ** 2)


def hand_tree(x: tuple[float, float]) -> str:
    mean, sharp = x
    if mean < 55:
        return "dark"
    if mean > 220:
        return "bright"
    if sharp < 65:
        return "blurry"
    return "good"


# ------------------------------------------------------------------ CART (Gini)
def gini(labels: list[str]) -> float:
    n = len(labels)
    return 1.0 - sum((c / n) ** 2 for c in Counter(labels).values()) if n else 0.0


def build(rows: list[tuple[tuple[float, float], str]], depth: int, max_depth: int = 3, min_leaf: int = 5):
    labels = [y for _, y in rows]
    majority = Counter(labels).most_common(1)[0][0]
    if depth == max_depth or len(set(labels)) == 1:
        return majority
    best = None
    for fi in range(len(FEATURES)):
        values = sorted({x[fi] for x, _ in rows})
        for a, b in zip(values, values[1:]):
            t = (a + b) / 2
            left = [y for x, y in rows if x[fi] < t]
            right = [y for x, y in rows if x[fi] >= t]
            if len(left) < min_leaf or len(right) < min_leaf:
                continue
            score = (len(left) * gini(left) + len(right) * gini(right)) / len(rows)
            if best is None or score < best[0]:
                best = (score, fi, t)
    if best is None or best[0] >= gini(labels):
        return majority
    _, fi, t = best
    return (fi, t,
            build([r for r in rows if r[0][fi] < t], depth + 1, max_depth, min_leaf),
            build([r for r in rows if r[0][fi] >= t], depth + 1, max_depth, min_leaf))


def predict(tree, x) -> str:
    while isinstance(tree, tuple):
        fi, t, left, right = tree
        tree = left if x[fi] < t else right
    return tree


def describe(tree, indent: str = "") -> list[str]:
    if not isinstance(tree, tuple):
        return [f"{indent}-> {tree}"]
    fi, t, left, right = tree
    return ([f"{indent}if {FEATURES[fi]} < {t:.1f}:"] + describe(left, indent + "  ")
            + [f"{indent}else:"] + describe(right, indent + "  "))


def run_experiment(per_class: int = 60, seed: int = 152) -> dict:
    rng = random.Random(seed)
    nrng = np.random.default_rng(seed)
    rows = []
    for label in CLASSES:
        for _ in range(per_class):
            rows.append((features(degrade(render_page(rng), label, rng, nrng)), label))
    rng.shuffle(rows)
    cut = int(len(rows) * 0.6)
    train, test = rows[:cut], rows[cut:]
    tree = build(train, 0)

    def score(fn) -> dict:
        hits = sum(fn(x) == y for x, y in test)
        per = {c: f"{sum(fn(x) == y for x, y in test if y == c)}/{sum(y == c for _, y in test)}" for c in CLASSES}
        return {"accuracy": round(hits / len(test), 4), "per_class": per}

    ranges = {c: {f: [round(min(x[i] for x, y in rows if y == c), 1), round(max(x[i] for x, y in rows if y == c), 1)]
                  for i, f in enumerate(FEATURES)} for c in CLASSES}
    return {
        "synthetic_pages": len(rows), "train": len(train), "test": len(test), "seed": seed,
        "hand_tree_test": score(hand_tree),
        "learned_tree_test": score(lambda x: predict(tree, x)),
        "learned_tree": describe(tree),
        "feature_ranges_by_class": ranges,
    }


if __name__ == "__main__":
    import json
    print(json.dumps(run_experiment(), indent=2))
