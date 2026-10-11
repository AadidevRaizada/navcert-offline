"""Paired raw-vs-enhanced evaluation of the NavCert Offline preprocessing on synthetic certificates.

Experiments: (a) page detection, (b) OCR CER/WER + field exact match per variant,
(c) quality-check flags, (d) threshold comparison, (e) stage runtime, (f) DCT compression.
Block matching (g) lives in block_matching.py.

    ml\\.venv\\Scripts\\python.exe ml\\ivp_eval\\run_experiments.py
"""
from __future__ import annotations

import argparse
import csv
import json
import platform
import statistics
import subprocess
import sys
import time
from collections import defaultdict
from pathlib import Path

import cv2
import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import ocr_metrics as om  # noqa: E402
import pipeline as pl  # noqa: E402
import synth  # noqa: E402

PAGE_SEED0 = 1000
CAPTURE_SEED0 = 100000
VARIANTS = ["raw", "rectified", "enhanced_gray", "enhanced_bw"]
EXPECTED_FLAG = {"low_light": "Too dark", "overexposure": "Too bright", "gaussian_blur": "Soft image",
                 "motion_blur": "Soft image"}
FIELD_LABELS = {f: synth.FIELD_LABELS[f] for f in synth.FIVE_FIELDS}


def conditions() -> list[tuple[str, int]]:
    return [("clean", 0)] + [(d, s) for d in synth.DEGRADATIONS for s in (1, 2, 3)]


def make_capture(pages, ci: int, j: int, n_pages: int, per: int):
    deg, sev = conditions()[ci]
    page = pages[(ci * per + j) % n_pages]
    cap = synth.capture(page, deg, max(sev, 1), CAPTURE_SEED0 + ci * 100 + j)
    cap.severity = sev
    return page, cap


def quad_iou(a: np.ndarray, b: np.ndarray) -> float:
    a32, b32 = a.astype(np.float32).reshape(-1, 1, 2), b.astype(np.float32).reshape(-1, 1, 2)
    inter, _ = cv2.intersectConvexConvex(a32, b32)
    union = cv2.contourArea(a32) + cv2.contourArea(b32) - inter
    return float(inter / union) if union > 0 else 0.0


def ocr_scores(image: np.ndarray, page) -> dict:
    lines = om.reconstruct_lines(om.run_ocr(image))
    hyp = "\n".join(lines)
    fields = om.field_exact(om.extract_fields(lines, FIELD_LABELS), page.fields)
    return {"cer": om.cer(page.text, hyp), "wer": om.wer(page.text, hyp),
            "word_recall": om.word_recall(page.text, hyp),
            "fields": fields, "field_em": sum(fields.values()) / len(fields)}


def cpu_name() -> str:
    try:
        out = subprocess.run(["powershell", "-NoProfile", "-Command", "(Get-CimInstance Win32_Processor).Name"],
                             capture_output=True, text=True, timeout=30).stdout.strip()
        return out or platform.processor()
    except Exception:
        return platform.processor()


def mean(xs):
    xs = list(xs)
    return float(np.mean(xs)) if xs else None


def experiment_main(pages, n_pages, per, rows_out):
    rows = []
    for ci, (deg, sev) in enumerate(conditions()):
        for j in range(per):
            page, cap = make_capture(pages, ci, j, n_pages, per)
            scan = pl.prepare(cap.image)
            row = {"degradation": deg, "severity": sev, "page_seed": page.seed, "font": page.font,
                   "capture_seed": CAPTURE_SEED0 + ci * 100 + j, "detected": scan.corners_full is not None,
                   "brightness": scan.quality.brightness, "sharpness": scan.quality.sharpness,
                   "quality_label": scan.quality.label}
            if scan.corners_full is not None:
                gt = pl.order_corners(cap.quad)
                err = np.linalg.norm(scan.corners_full - gt, axis=1)
                diag = float(np.hypot(*(gt[2] - gt[0])))
                row.update(iou=quad_iou(scan.corners_full, gt), corner_err_px=float(err.mean()),
                           corner_err_pct_diag=float(100 * err.mean() / diag))
            else:
                row.update(iou=0.0, corner_err_px=None, corner_err_pct_diag=None)
            images = {"raw": scan.normalized, "rectified": scan.rectified,
                      "enhanced_gray": pl.enhance_for_ocr(scan.rectified, "AUTO"),
                      "enhanced_bw": pl.enhance_for_ocr(scan.rectified, "BLACK_WHITE")}
            for v, im in images.items():
                s = ocr_scores(im, page)
                row[f"cer_{v}"], row[f"wer_{v}"], row[f"field_em_{v}"] = s["cer"], s["wer"], s["field_em"]
                row[f"word_recall_{v}"] = s["word_recall"]
                for f, ok in s["fields"].items():
                    row[f"field_{f}_{v}"] = int(ok)
            rows.append(row)
            print(f"[main] {deg} s{sev} #{j}: det={row['detected']} iou={row['iou']:.3f} "
                  f"cer raw={row['cer_raw']:.3f} rect={row['cer_rectified']:.3f} "
                  f"gray={row['cer_enhanced_gray']:.3f} bw={row['cer_enhanced_bw']:.3f}", flush=True)
    with rows_out.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)
    return rows


def summarise_main(rows):
    out = {"by_condition": [], "by_degradation": [], "overall": {}, "detection": {}, "quality_check": {}}
    groups = defaultdict(list)
    for r in rows:
        groups[(r["degradation"], r["severity"])].append(r)
    for (deg, sev), rs in groups.items():
        e = {"degradation": deg, "severity": sev, "n": len(rs),
             "detection_rate": mean(r["detected"] for r in rs),
             "iou_ge_0_9_rate": mean(r["iou"] >= 0.9 for r in rs),
             "mean_iou_detected": mean(r["iou"] for r in rs if r["detected"]),
             "mean_corner_err_px": mean(r["corner_err_px"] for r in rs if r["detected"])}
        for v in VARIANTS:
            e[f"cer_{v}"] = mean(r[f"cer_{v}"] for r in rs)
            e[f"wer_{v}"] = mean(r[f"wer_{v}"] for r in rs)
            e[f"word_recall_{v}"] = mean(r[f"word_recall_{v}"] for r in rs)
            e[f"field_em_{v}"] = mean(r[f"field_em_{v}"] for r in rs)
        out["by_condition"].append(e)
    by_deg = defaultdict(list)
    for r in rows:
        by_deg[r["degradation"]].append(r)
    for deg, rs in by_deg.items():
        e = {"degradation": deg, "n": len(rs), "detection_rate": mean(r["detected"] for r in rs),
             "iou_ge_0_9_rate": mean(r["iou"] >= 0.9 for r in rs),
             "mean_iou_detected": mean(r["iou"] for r in rs if r["detected"]),
             "mean_corner_err_px": mean(r["corner_err_px"] for r in rs if r["detected"]),
             "median_corner_err_px": float(np.median([r["corner_err_px"] for r in rs if r["detected"]]))
             if any(r["detected"] for r in rs) else None}
        for v in VARIANTS:
            e[f"cer_{v}"] = mean(r[f"cer_{v}"] for r in rs)
            e[f"median_cer_{v}"] = float(np.median([r[f"cer_{v}"] for r in rs]))
            e[f"wer_{v}"] = mean(r[f"wer_{v}"] for r in rs)
            e[f"word_recall_{v}"] = mean(r[f"word_recall_{v}"] for r in rs)
            e[f"field_em_{v}"] = mean(r[f"field_em_{v}"] for r in rs)
        out["by_degradation"].append(e)
    o = {"n": len(rows)}
    for v in VARIANTS:
        o[f"cer_{v}"] = mean(r[f"cer_{v}"] for r in rows)
        o[f"median_cer_{v}"] = float(np.median([r[f"cer_{v}"] for r in rows]))
        o[f"wer_{v}"] = mean(r[f"wer_{v}"] for r in rows)
        o[f"word_recall_{v}"] = mean(r[f"word_recall_{v}"] for r in rows)
        o[f"field_em_{v}"] = mean(r[f"field_em_{v}"] for r in rows)
        o[f"field_exact_total_{v}"] = int(sum(r[f"field_{f}_{v}"] for r in rows for f in synth.FIVE_FIELDS))
        o[f"per_field_{v}"] = {f: int(sum(r[f"field_{f}_{v}"] for r in rows)) for f in synth.FIVE_FIELDS}
        o[f"pages_cer_improved_vs_raw_{v}"] = int(sum(r[f"cer_{v}"] < r["cer_raw"] for r in rows))
    o["field_slots"] = len(rows) * len(synth.FIVE_FIELDS)
    # sign test style counts for the main claim: enhanced_gray vs raw, paired
    diffs = [r["cer_raw"] - r["cer_enhanced_gray"] for r in rows]
    o["paired_cer_reduction_gray_vs_raw"] = {"mean": mean(diffs), "median": float(np.median(diffs)),
                                             "better": int(sum(d > 0 for d in diffs)),
                                             "worse": int(sum(d < 0 for d in diffs)),
                                             "tied": int(sum(d == 0 for d in diffs))}
    rng = np.random.default_rng(7)
    boots = [np.mean(rng.choice(diffs, len(diffs))) for _ in range(2000)]
    o["paired_cer_reduction_gray_vs_raw"]["bootstrap95"] = [float(np.percentile(boots, 2.5)),
                                                             float(np.percentile(boots, 97.5))]
    out["overall"] = o
    det = [r for r in rows]
    out["detection"] = {"n": len(det), "detected": int(sum(r["detected"] for r in det)),
                        "iou_ge_0_9": int(sum(r["iou"] >= 0.9 for r in det)),
                        "mean_iou_detected": mean(r["iou"] for r in det if r["detected"]),
                        "median_corner_err_px": float(np.median([r["corner_err_px"] for r in det if r["detected"]]))}
    labels = ["Image looks good", "Too dark", "Too bright", "Soft image"]
    conf = {e: {p: 0 for p in labels} for e in labels}
    for r in rows:
        expected = EXPECTED_FLAG.get(r["degradation"], "Image looks good")
        conf[expected][r["quality_label"]] += 1
    rates = []
    for (deg, sev), rs in groups.items():
        rates.append({"degradation": deg, "severity": sev, "n": len(rs),
                      **{p: sum(r["quality_label"] == p for r in rs) for p in labels},
                      "mean_brightness": mean(r["brightness"] for r in rs),
                      "mean_laplacian_var": mean(r["sharpness"] for r in rs)})
    correct = sum(conf[k][k] for k in labels)
    out["quality_check"] = {"labels": labels, "confusion_expected_x_predicted": conf,
                            "accuracy": correct / len(rows), "correct": correct, "n": len(rows),
                            "by_condition": rates}
    return out


def experiment_threshold(pages, n_pages, per):
    ci0 = [i for i, c in enumerate(conditions()) if c[0] == "shadow"]
    rows = []
    for ci in ci0:
        for j in range(per):
            page, cap = make_capture(pages, ci, j, n_pages, per)
            rect = synth.warp_to_page(cap.image, cap.quad)          # oracle corners isolate thresholding
            gray = cv2.cvtColor(rect, cv2.COLOR_BGR2GRAY)
            pre = {"gray": gray, "clahe_median": pl.clahe_median(gray)}
            variants = {}
            for pname, g in pre.items():
                variants[f"global127_{pname}"] = cv2.threshold(g, 127, 255, cv2.THRESH_BINARY)[1]
                variants[f"otsu_{pname}"] = cv2.threshold(g, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)[1]
                variants[f"adaptive_{pname}"] = cv2.adaptiveThreshold(
                    g, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, pl.ADAPTIVE_BLOCK, pl.ADAPTIVE_C)
            row = {"severity": conditions()[ci][1], "page_seed": page.seed}
            truth = page.ink_mask
            for name, b in variants.items():
                ink = b < 128
                tp = float(np.sum(ink & truth))
                prec = tp / max(1.0, float(ink.sum()))
                rec = tp / max(1.0, float(truth.sum()))
                row[f"f1_{name}"] = 2 * prec * rec / max(1e-9, prec + rec)
                row[f"cer_{name}"] = ocr_scores(pl.add_border(b), page)["cer"]
            row["cer_gray_no_threshold"] = ocr_scores(pl.add_border(gray), page)["cer"]
            rows.append(row)
            print(f"[threshold] s{row['severity']} #{j}: " + " ".join(
                f"{k[4:]}={v:.3f}" for k, v in row.items() if k.startswith("cer_")), flush=True)
    names = [k[4:] for k in rows[0] if k.startswith("cer_")]
    summary = {"n": len(rows), "input": "shadow captures rectified with ground-truth corners to 1240x1754",
               "methods": {}}
    for nme in names:
        summary["methods"][nme] = {"mean_cer": mean(r[f"cer_{nme}"] for r in rows),
                                   "mean_ink_f1": mean(r[f"f1_{nme}"] for r in rows) if f"f1_{nme}" in rows[0] else None,
                                   "by_severity_cer": {s: mean(r[f"cer_{nme}"] for r in rows if r["severity"] == s)
                                                       for s in (1, 2, 3)}}
    return summary


# ---------------------------------------------------------------- DCT study
Q50 = np.array([
    [16, 11, 10, 16, 24, 40, 51, 61], [12, 12, 14, 19, 26, 58, 60, 55], [14, 13, 16, 24, 40, 57, 69, 56],
    [14, 17, 22, 29, 51, 87, 80, 62], [18, 22, 37, 56, 68, 109, 103, 77], [24, 35, 55, 64, 81, 104, 113, 92],
    [49, 64, 78, 87, 103, 121, 120, 101], [72, 92, 95, 98, 112, 100, 103, 99]], np.float64)


def dct_matrix(n=8):
    c = np.zeros((n, n))
    for k in range(n):
        for i in range(n):
            c[k, i] = (np.sqrt(1 / n) if k == 0 else np.sqrt(2 / n)) * np.cos(np.pi * (2 * i + 1) * k / (2 * n))
    return c


def blockify(img):
    h, w = img.shape
    return img.reshape(h // 8, 8, w // 8, 8).transpose(0, 2, 1, 3)


def unblockify(blocks):
    a, b = blocks.shape[:2]
    return blocks.transpose(0, 2, 1, 3).reshape(a * 8, b * 8)


def dct_codec(gray: np.ndarray, quality: int | None = None, keep: int | None = None):
    h, w = gray.shape
    H, W = (h + 7) // 8 * 8, (w + 7) // 8 * 8
    x = cv2.copyMakeBorder(gray, 0, H - h, 0, W - w, cv2.BORDER_REPLICATE).astype(np.float64) - 128
    C = dct_matrix()
    coef = C @ blockify(x) @ C.T
    if quality is not None:
        s = 5000 / quality if quality < 50 else 200 - 2 * quality
        q = np.clip(np.floor((Q50 * s + 50) / 100), 1, 255)
        qc = np.round(coef / q)
        nonzero = float(np.mean(qc != 0))
        coef = qc * q
    else:
        mask = np.zeros((8, 8))
        mask[:keep, :keep] = 1
        coef = coef * mask
        nonzero = keep * keep / 64
    rec = unblockify(C.T @ coef @ C) + 128
    return np.clip(np.round(rec), 0, 255).astype(np.uint8)[:h, :w], nonzero


def psnr(a, b):
    mse = np.mean((a.astype(np.float64) - b.astype(np.float64)) ** 2)
    return float(10 * np.log10(255 ** 2 / mse)) if mse > 0 else None  # None = identical (lossless)


def experiment_dct(pages, n=6):
    qualities = [90, 75, 50, 30, 20, 10, 5, 2]
    keeps = [8, 6, 4, 3, 2, 1]
    rows = []
    for page in pages[:n]:
        gray = cv2.cvtColor(page.image, cv2.COLOR_BGR2GRAY)
        base = ocr_scores(pl.add_border(gray), page)["cer"]
        rows.append({"page_seed": page.seed, "setting": "original", "cer": base, "psnr": None, "nonzero": 1.0})
        for q in qualities:
            rec, nz = dct_codec(gray, quality=q)
            ok, enc = cv2.imencode(".jpg", gray, [cv2.IMWRITE_JPEG_QUALITY, q])
            rows.append({"page_seed": page.seed, "setting": f"q{q}", "quality": q, "psnr": psnr(gray, rec),
                         "nonzero": nz, "cer": ocr_scores(pl.add_border(rec), page)["cer"],
                         "opencv_jpeg_bpp": 8.0 * len(enc) / gray.size})
        for k in keeps:
            rec, nz = dct_codec(gray, keep=k)
            rows.append({"page_seed": page.seed, "setting": f"keep{k}x{k}", "keep": k, "psnr": psnr(gray, rec),
                         "nonzero": nz, "cer": ocr_scores(pl.add_border(rec), page)["cer"]})
        print(f"[dct] page {page.seed} done", flush=True)
    out = {"n_pages": n, "quantisation": [], "zonal": [],
           "original_mean_cer": mean(r["cer"] for r in rows if r["setting"] == "original"),
           "opencv_png_bpp": mean(8.0 * len(cv2.imencode(".png", cv2.cvtColor(p.image, cv2.COLOR_BGR2GRAY))[1])
                                  / (synth.PAGE_W * synth.PAGE_H) for p in pages[:n])}
    for q in qualities:
        rs = [r for r in rows if r.get("quality") == q]
        out["quantisation"].append({"quality": q, "mean_psnr_db": mean(r["psnr"] for r in rs),
                                    "mean_nonzero_coeff_fraction": mean(r["nonzero"] for r in rs),
                                    "mean_cer": mean(r["cer"] for r in rs),
                                    "mean_opencv_jpeg_bpp": mean(r["opencv_jpeg_bpp"] for r in rs)})
    for k in keeps:
        rs = [r for r in rows if r.get("keep") == k]
        out["zonal"].append({"keep": f"{k}x{k}", "coeff_fraction": k * k / 64,
                             "mean_psnr_db": None if any(r["psnr"] is None for r in rs) else mean(r["psnr"] for r in rs), "mean_cer": mean(r["cer"] for r in rs)})
    return out


def experiment_runtime(pages, n=12, repeats=5):
    stages = defaultdict(list)
    for i in range(n):
        cap = synth.capture(pages[i], "clean", 1, 900000 + i)
        img = cap.image
        for _ in range(repeats):
            t0 = time.perf_counter(); normalized = pl.scale_to_maximum(img, pl.MAX_OCR_DIMENSION)
            analysis = pl.scale_to_maximum(normalized, pl.MAX_ANALYSIS_DIMENSION); t1 = time.perf_counter()
            pl.measure_quality(analysis); t2 = time.perf_counter()
            corners = pl.detect_corners(analysis); t3 = time.perf_counter()
            if corners is None:
                continue
            full = corners * np.array([normalized.shape[1] / analysis.shape[1], normalized.shape[0] / analysis.shape[0]])
            rect = pl.warp_perspective(normalized, full); t4 = time.perf_counter()
            gray_out = pl.enhance_for_ocr(rect, "AUTO"); t5 = time.perf_counter()
            pl.enhance_for_ocr(rect, "BLACK_WHITE"); t6 = time.perf_counter()
            for k, (a, b) in {"resize_to_analysis": (t0, t1), "quality_check": (t1, t2), "page_detection": (t2, t3),
                              "perspective_warp": (t3, t4), "enhance_gray": (t4, t5), "enhance_bw": (t5, t6)}.items():
                stages[k].append(1000 * (b - a))
        t = time.perf_counter(); om._engine().predict(cv2.cvtColor(gray_out, cv2.COLOR_GRAY2BGR))
        stages["ocr_pp_ocrv6_small"].append(1000 * (time.perf_counter() - t))
    return {"note": "Desktop CPU timing of the Python/OpenCV port, not phone timing.",
            "frame": f"{synth.FRAME_W}x{synth.FRAME_H} capture, analysis <=1600 px",
            "n_images": n, "repeats": repeats,
            "median_ms": {k: float(statistics.median(v)) for k, v in stages.items()},
            "samples": {k: len(v) for k, v in stages.items()}}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pages", type=int, default=40)
    ap.add_argument("--per-condition", type=int, default=8)
    ap.add_argument("--skip", nargs="*", default=[])
    args = ap.parse_args()
    t_start = time.time()
    pages = [synth.render_page(PAGE_SEED0 + i) for i in range(args.pages)]
    gt_dir = HERE / "data" / "ground_truth"
    gt_dir.mkdir(parents=True, exist_ok=True)
    for p in pages:
        (gt_dir / f"page_{p.seed}.json").write_text(json.dumps({"seed": p.seed, "font": p.font, "lines": p.lines,
                                                                 "fields": p.fields}, indent=1), encoding="utf-8")
    results_path = HERE / "results.json"
    results = json.loads(results_path.read_text()) if results_path.exists() else {}
    results["setup"] = {
        "pages": args.pages, "captures_per_condition": args.per_condition,
        "conditions": [f"{d}:{s}" for d, s in conditions()], "degradation_parameters": synth.DEGRADATIONS,
        "base_capture": synth.BASE, "page_px": [synth.PAGE_W, synth.PAGE_H], "frame_px": [synth.FRAME_W, synth.FRAME_H],
        "seeds": {"page": f"{PAGE_SEED0}+i", "capture": f"{CAPTURE_SEED0}+100*condition+j"},
        "ocr": om.engine_versions(), "cpu": cpu_name(), "python": platform.python_version(),
        "os": platform.platform(), "numpy": np.__version__,
        "normalisation": "lower-case, unify quotes/dashes, collapse whitespace and line breaks to one space",
    }
    if "main" not in args.skip:
        rows = experiment_main(pages, args.pages, args.per_condition, HERE / "results.csv")
        results["main"] = summarise_main(rows)
    if "threshold" not in args.skip:
        results["threshold"] = experiment_threshold(pages, args.pages, args.per_condition)
    if "dct" not in args.skip:
        results["dct"] = experiment_dct(pages)
    if "runtime" not in args.skip:
        results["runtime"] = experiment_runtime(pages)
    bm_path = HERE / "block_matching.json"   # produced by block_matching.py
    if bm_path.exists():
        results["block_matching"] = json.loads(bm_path.read_text())
    results["setup"]["wall_clock_s"] = round(time.time() - t_start, 1)
    results_path.write_text(json.dumps(results, indent=2), encoding="utf-8")
    print(f"done in {time.time() - t_start:.0f}s")


if __name__ == "__main__":
    main()
