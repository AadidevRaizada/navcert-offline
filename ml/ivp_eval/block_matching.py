"""Block-matching motion estimation on synthetic camera-shake frame pairs with known motion.

Frame 2 is frame 1 translated by an integer (dx, dy) plus sensor noise, so every block's
true motion vector is known. Compared: exhaustive search, three-step search (TSS) and
2D-logarithmic search, with 16x16 blocks, search range p = 7 and mean-absolute-difference cost.

    ml\\.venv\\Scripts\\python.exe ml\\ivp_eval\\block_matching.py
"""
from __future__ import annotations

import json
import sys
import time
from pathlib import Path

import cv2
import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import synth  # noqa: E402

BLOCK, P = 16, 7
SEED = 152
TEXTURE_STD = 10.0


def cost(cur, ref, x, y, u, v):
    a = cur[y:y + BLOCK, x:x + BLOCK]
    b = ref[y + v:y + v + BLOCK, x + u:x + u + BLOCK]
    return float(np.mean(np.abs(a - b)))


def exhaustive(cur, ref, x, y):
    visited = [(u, v) for v in range(-P, P + 1) for u in range(-P, P + 1)]
    costs = {pt: cost(cur, ref, x, y, *pt) for pt in visited}
    best = min(visited, key=lambda pt: (costs[pt], abs(pt[0]) + abs(pt[1])))
    return best, costs[best], visited


def three_step(cur, ref, x, y):
    costs, order = {}, []

    def c(pt):
        if pt not in costs:
            costs[pt] = cost(cur, ref, x, y, *pt)
            order.append(pt)
        return costs[pt]
    centre = (0, 0)
    c(centre)
    for step in (4, 2, 1):
        cands = [(centre[0] + du * step, centre[1] + dv * step) for dv in (-1, 0, 1) for du in (-1, 0, 1)]
        cands = [pt for pt in cands if abs(pt[0]) <= P and abs(pt[1]) <= P]
        centre = min(cands, key=c)
    return centre, costs[centre], order


def two_d_log(cur, ref, x, y):
    costs, order = {}, []

    def c(pt):
        if pt not in costs:
            costs[pt] = cost(cur, ref, x, y, *pt)
            order.append(pt)
        return costs[pt]
    centre, step = (0, 0), 4
    c(centre)
    while True:
        if step == 1:
            cands = [(centre[0] + du, centre[1] + dv) for dv in (-1, 0, 1) for du in (-1, 0, 1)]
            cands = [pt for pt in cands if abs(pt[0]) <= P and abs(pt[1]) <= P]
            centre = min(cands, key=c)
            return centre, costs[centre], order
        cands = [centre] + [(centre[0] + du, centre[1] + dv) for du, dv in ((step, 0), (-step, 0), (0, step), (0, -step))]
        cands = [pt for pt in cands if abs(pt[0]) <= P and abs(pt[1]) <= P]
        best = min(cands, key=c)
        if best == centre or abs(best[0]) == P or abs(best[1]) == P:
            step //= 2
        centre = best


METHODS = {"exhaustive": exhaustive, "three_step": three_step, "2d_log": two_d_log}


def make_pair(i: int, rng: np.random.Generator):
    page = synth.render_page(1000 + i)
    cap = synth.capture(page, "clean", 1, 700000 + i)
    ref = cv2.resize(cv2.cvtColor(cap.image, cv2.COLOR_BGR2GRAY), (480, 640), interpolation=cv2.INTER_AREA)
    dx, dy = (int(v) for v in rng.integers(-P, P + 1, 2))
    cur = np.roll(ref, (dy, dx), axis=(0, 1)).astype(np.float32)   # content moves by (dx, dy)
    cur += rng.normal(0, 2.0, cur.shape).astype(np.float32)
    return ref.astype(np.float32), cur, (-dx, -dy)                   # vector from current block to reference


def run(n_pairs: int = 10, prefilter_sigma: float | None = None):
    rng = np.random.default_rng(SEED)
    per = {m: {"points": [], "err": [], "err_tex": [], "exact": [], "exact_tex": [], "sad": [], "ms": []} for m in METHODS}
    shifts = []
    for i in range(n_pairs):
        ref, cur, true_mv = make_pair(i, rng)
        if prefilter_sigma:  # low-pass both frames: smoother error surface, same true motion
            ref = cv2.GaussianBlur(ref, (0, 0), prefilter_sigma)
            cur = cv2.GaussianBlur(cur, (0, 0), prefilter_sigma)
        shifts.append(true_mv)
        H, W = ref.shape
        blocks = [(x, y) for y in range(P + BLOCK, H - BLOCK - P - BLOCK, BLOCK)
                  for x in range(P + BLOCK, W - BLOCK - P - BLOCK, BLOCK)]
        for m, fn in METHODS.items():
            t = time.perf_counter()
            for x, y in blocks:
                mv, sad, visited = fn(cur, ref, x, y)
                e = float(np.hypot(mv[0] - true_mv[0], mv[1] - true_mv[1]))
                textured = float(cur[y:y + BLOCK, x:x + BLOCK].std()) > TEXTURE_STD
                d = per[m]
                d["points"].append(len(set(visited)))
                d["err"].append(e)
                d["exact"].append(e == 0)
                d["sad"].append(sad)
                if textured:
                    d["err_tex"].append(e)
                    d["exact_tex"].append(e == 0)
            per[m]["ms"].append(1000 * (time.perf_counter() - t))
        print(f"pair {i}: true mv {true_mv}, {len(blocks)} blocks", flush=True)
    out = {"block": BLOCK, "search_range": P, "cost": "mean absolute difference per pixel (SAD / 256)",
           "pairs": n_pairs, "prefilter_gaussian_sigma": prefilter_sigma, "frame_px": [480, 640], "noise_sigma": 2.0, "seed": SEED,
           "true_vectors": shifts, "blocks_total": len(per["exhaustive"]["points"]),
           "textured_blocks": len(per["exhaustive"]["err_tex"]), "texture_rule": f"block std > {TEXTURE_STD}",
           "methods": {}}
    for m, d in per.items():
        out["methods"][m] = {
            "mean_search_points_per_block": float(np.mean(d["points"])),
            "max_search_points_per_block": int(np.max(d["points"])),
            "mean_vector_error_px_all": float(np.mean(d["err"])),
            "mean_vector_error_px_textured": float(np.mean(d["err_tex"])),
            "exact_vector_rate_all": float(np.mean(d["exact"])),
            "exact_vector_rate_textured": float(np.mean(d["exact_tex"])),
            "mean_mad_cost": float(np.mean(d["sad"])),
            "median_python_ms_per_frame": float(np.median(d["ms"])),
        }
    return out


if __name__ == "__main__":
    res = {"sharp_frames": run(), "lowpass_sigma2_frames": run(prefilter_sigma=2.0)}
    (HERE / "block_matching.json").write_text(json.dumps(res, indent=2), encoding="utf-8")
    for k, v in res.items():
        print(k, json.dumps(v["methods"], indent=1))
