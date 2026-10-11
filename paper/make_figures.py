"""Paper-size figures for the NavCert Offline IEEE paper.

Every plotted number is read from ml/ivp_eval/results.json, results.csv or block_matching.json.
Example images are regenerated from the benchmark's own seeds (synthetic pages only).
Run with the ml venv:  ml\\.venv\\Scripts\\python.exe paper\\make_figures.py
"""
from __future__ import annotations

import csv
import json
import sys
from pathlib import Path

import cv2
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
from matplotlib.patches import FancyArrowPatch, FancyBboxPatch  # noqa: E402
from PIL import Image  # noqa: E402

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
EVAL = ROOT / "ml" / "ivp_eval"
sys.path.insert(0, str(EVAL))
import block_matching as bm  # noqa: E402
import pipeline as pl  # noqa: E402
import synth  # noqa: E402
from run_experiments import conditions, dct_codec, make_capture  # noqa: E402

OUT = HERE / "figures"
OUT.mkdir(exist_ok=True)
R = json.loads((EVAL / "results.json").read_text())
BM = json.loads((EVAL / "block_matching.json").read_text())
INK, MUTED, BLUE, GOLD, RED, GREY = "#0D1B2A", "#5B6472", "#1F4BD8", "#E0A800", "#D62828", "#A3A9B2"
DPI = 600
COL, FULL = 3.5, 7.16
plt.rcParams.update({
    "font.family": "serif", "font.serif": ["Times New Roman"], "mathtext.fontset": "stix",
    "font.size": 7.5, "axes.titlesize": 8, "axes.labelsize": 7.5, "xtick.labelsize": 7, "ytick.labelsize": 7,
    "legend.fontsize": 7, "axes.edgecolor": MUTED, "axes.linewidth": 0.6, "axes.labelcolor": INK,
    "text.color": INK, "xtick.color": INK, "ytick.color": INK, "axes.spines.top": False,
    "axes.spines.right": False, "axes.grid": True, "grid.color": "#E3E6EA", "grid.linewidth": 0.4,
    "axes.axisbelow": True, "xtick.major.width": 0.5, "ytick.major.width": 0.5,
    "xtick.major.size": 2, "ytick.major.size": 2, "figure.facecolor": "white", "savefig.facecolor": "white",
})
VARS = {"raw": ("Raw photo", GREY), "rectified": ("Rectified", GOLD),
        "enhanced_gray": ("Rectified + enhanced (gray, Auto)", BLUE), "enhanced_bw": ("Rectified + B/W adaptive", INK)}
DEGS = ["clean", "tilt", "shadow", "low_light", "overexposure", "gaussian_blur", "motion_blur", "noise", "jpeg"]
DEG_NAME = {"clean": "Clean", "tilt": "Tilt", "shadow": "Shadow", "low_light": "Low light",
            "overexposure": "Over-\nexposure", "gaussian_blur": "Gaussian\nblur", "motion_blur": "Motion\nblur",
            "noise": "Noise", "jpeg": "JPEG"}
PAGES = [synth.render_page(1000 + i) for i in range(R["setup"]["pages"])]
PER = R["setup"]["captures_per_condition"]


def save(fig, name):
    fig.savefig(OUT / name, dpi=DPI, bbox_inches="tight", pad_inches=0.02)
    plt.close(fig)
    print(name, Image.open(OUT / name).size)


def rgb(img):
    return cv2.cvtColor(img, cv2.COLOR_BGR2RGB) if img.ndim == 3 else img


def bare(ax):
    ax.set_xticks([]); ax.set_yticks([]); ax.grid(False)
    for s in ax.spines.values():
        s.set_visible(True); s.set_color("#C9CDD3"); s.set_linewidth(0.4)


def capture_for(deg, sev, j):
    return make_capture(PAGES, conditions().index((deg, sev)), j, len(PAGES), PER)


# ---------------------------------------------------------------- bootstrap CIs from per-capture rows
def bootstrap_stats():
    rows = list(csv.DictReader(open(EVAL / "results.csv")))
    rng = np.random.default_rng(152)
    B = 10_000
    out = {}
    for d in DEGS + ["all"]:
        sub = rows if d == "all" else [r for r in rows if r["degradation"] == d]
        n = len(sub)
        idx = rng.integers(0, n, (B, n))
        entry = {"n": n}
        for v in VARS:
            for m in ("cer", "field_em"):
                x = np.array([float(r[f"{m}_{v}"]) for r in sub])
                bs = x[idx].mean(axis=1)
                entry[f"{m}_{v}"] = [float(x.mean()), float(np.percentile(bs, 2.5)), float(np.percentile(bs, 97.5))]
        diff = np.array([float(r["cer_raw"]) - float(r["cer_enhanced_gray"]) for r in sub])
        bs = diff[idx].mean(axis=1)
        entry["paired_cer_raw_minus_gray"] = [float(diff.mean()), float(np.percentile(bs, 2.5)), float(np.percentile(bs, 97.5))]
        diff = np.array([float(r["cer_raw"]) - float(r["cer_rectified"]) for r in sub])
        bs = diff[idx].mean(axis=1)
        entry["paired_cer_raw_minus_rect"] = [float(diff.mean()), float(np.percentile(bs, 2.5)), float(np.percentile(bs, 97.5))]
        out[d] = entry
    out["_method"] = "Percentile bootstrap, 10,000 resamples of captures within each group, seed 152, from ml/ivp_eval/results.csv"
    (OUT / "derived_stats.json").write_text(json.dumps(out, indent=1))
    return out


# ---------------------------------------------------------------- figures
def fig_architecture():
    fig = plt.figure(figsize=(FULL, 2.2))
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_xlim(0, 100); ax.set_ylim(0, 36); ax.axis("off")

    def box(x, y, w, h, title, body, fc="#F4F6FA", ec=MUTED, tc=BLUE):
        ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.2,rounding_size=0.8",
                                    fc=fc, ec=ec, lw=0.6))
        ax.text(x + w / 2, y + h - 1.0, title, ha="center", va="top", fontsize=6.6, weight="bold", color=tc)
        ax.text(x + w / 2, y + h - 3.9, body.replace("|", chr(10)), ha="center", va="top", fontsize=5.8, color=INK, linespacing=1.2)

    def arrow(a, b, color=MUTED, ls="-"):
        ax.add_patch(FancyArrowPatch(a, b, arrowstyle="-|>", mutation_scale=6, lw=0.7, color=color, linestyle=ls))

    box(0.5, 17, 13, 15, "Capture", "Camera intent|(TakePicture) or|gallery import;|optional manual|crop editor")
    box(16, 1, 25, 31, "DocumentScanner (OpenCV)",
        "Downscale only: OCR copy ≤ 3200 px,|analysis copy ≤ 1600 px||"
        "Quality on the whole analysis frame:|mean < 55 dark, > 220 bright,|Laplacian variance < 65 soft||"
        "Gray → Gaussian 5×5 → Canny 55/170|→ 5×5 closing → contours →|"
        "Douglas–Peucker → convex quad|≥ 15 % of frame → homography warp||"
        "CLAHE 2.2, 8×8 → median 3×3 →|unsharp (Auto, Clean) or adaptive|"
        "threshold 35/11 (B&W) → 18 px|white border; every mode is gray",
        fc="#EEF2FB", ec=BLUE)
    box(44, 19, 16, 13, "ML Kit Text v2", "Bundled Latin model;|blocks, lines and|bounding boxes")
    box(44, 1, 16, 15, "PP-OCRv5 rec (ONNX)", "Handwriting mode only:|line crops from ML Kit|boxes, 48 px high, CTC;|confidence < 0.20 →|ML Kit line kept")
    box(63, 19, 17, 13, "Layout + correction", "Block/line ordering,|0–6 space indent;|confusion map +|Levenshtein dictionary")
    box(82.5, 19, 17, 13, "Five fields", "Label rules first, then|MiniLM int8 ONNX,|then MobileBERT|(printed / Auto only)")
    box(63, 1, 17, 15, "Local storage", "App-private files:|text, JPEG preview,|folders, notes;|no INTERNET|permission")
    box(82.5, 1, 17, 15, "Ask documents", "MobileBERT QA over|1,200-character chunks|(180 overlap) of|saved documents")
    box(0.5, 1, 13, 13, "Pipeline Lab", "11 syllabus stages on|the current scan or|the synthetic sample;|reuses scanner stages", fc="#FFF7DC", ec=GOLD, tc="#8A6400")
    arrow((13.8, 24.5), (15.7, 24.5))
    arrow((41.3, 25.5), (43.7, 25.5))
    arrow((52, 18.7), (52, 16.3))
    arrow((60.3, 25.5), (62.7, 25.5))
    arrow((80.3, 25.5), (82.2, 25.5))
    arrow((60.3, 8.5), (62.7, 8.5))
    arrow((91, 18.7), (91, 16.3))
    arrow((71.5, 18.7), (71.5, 16.3))
    arrow((13.8, 7.5), (15.7, 7.5), color="#B08A00", ls="--")
    ax.text(50, 35.6, "Every stage runs on the phone; no stage makes a network call", ha="center", va="top",
            fontsize=6.3, style="italic", color=MUTED)
    fig.savefig(OUT / "fig_architecture.png", dpi=DPI, facecolor="white")
    plt.close(fig)
    print("fig_architecture.png")


def fig_pipeline():
    page, cap = capture_for("tilt", 2, 1)
    scan = pl.prepare(cap.image)
    edges, closed = pl.edge_map(scan.analysis)
    quad = scan.analysis.copy()
    contours, _ = cv2.findContours(closed, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    cv2.drawContours(quad, contours, -1, (180, 180, 180), 2)
    pts = scan.corners_analysis.astype(np.int32)
    cv2.polylines(quad, [pts], True, (216, 75, 31), 9)
    for p in pts:
        cv2.circle(quad, tuple(int(v) for v in p), 20, (44, 199, 255), -1)
        cv2.circle(quad, tuple(int(v) for v in p), 20, (42, 27, 13), 4)
    gray = pl.enhance_for_ocr(scan.rectified, "AUTO")
    bw = pl.enhance_for_ocr(scan.rectified, "BLACK_WHITE")
    stages = [("(a) Camera frame", scan.normalized), ("(b) Canny 55/170 + 5x5 close", 255 - closed),
              ("(c) Largest convex quad", quad), ("(d) Perspective warp", scan.rectified),
              ("(e) Auto enhancement", gray), ("(f) B&W threshold", bw)]
    fig, axes = plt.subplots(1, 6, figsize=(FULL, 1.95))
    for ax, (title, im) in zip(axes, stages):
        ax.imshow(rgb(im), cmap="gray", vmin=0, vmax=255)
        ax.set_xlabel(title, fontsize=7, labelpad=2)
        bare(ax)
    fig.tight_layout(w_pad=0.3)
    save(fig, "fig_pipeline_strip.png")


def fig_histogram():
    page, cap = capture_for("shadow", 2, 0)
    scan = pl.prepare(cap.image)
    g = cv2.cvtColor(scan.rectified, cv2.COLOR_BGR2GRAY)
    variants = [("Rectified gray", g, GREY), ("Global HE", cv2.equalizeHist(g), GOLD),
                ("CLAHE 2.2, 8x8", cv2.createCLAHE(2.2, (8, 8)).apply(g), BLUE)]
    fig, axes = plt.subplots(2, 3, figsize=(COL, 2.15), gridspec_kw={"height_ratios": [1.75, 0.9]})
    top = max(np.bincount(im.ravel(), minlength=256).max() / im.size for _, im, _ in variants) * 1.1
    for k, (name, im, col) in enumerate(variants):
        axes[0, k].imshow(im, cmap="gray", vmin=0, vmax=255)
        axes[0, k].set_title(name, fontsize=7.5, pad=2)
        bare(axes[0, k])
        h = np.bincount(im.ravel(), minlength=256) / im.size
        a = axes[1, k]
        a.fill_between(np.arange(256), h * 100, color=col, alpha=0.9, step="mid", lw=0)
        a.set_xlim(0, 255); a.set_ylim(0, top * 100)
        a.set_xticks([0, 128, 255]); a.set_xlabel("Gray level", labelpad=1)
        if k == 0:
            a.set_ylabel("Pixels (%)")
        else:
            a.set_yticklabels([])
    fig.tight_layout(h_pad=0.3, w_pad=0.4)
    save(fig, "fig_histogram_clahe.png")


def fig_cer_field(stats):
    short = {"clean": "Clean", "tilt": "Tilt", "shadow": "Shadow", "low_light": "Low|light",
             "overexposure": "Over-|exp.", "gaussian_blur": "Gauss.|blur", "motion_blur": "Motion|blur",
             "noise": "Noise", "jpeg": "JPEG"}
    short = {k: s.replace("|", chr(10)) for k, s in short.items()}
    x = np.arange(len(DEGS))
    w = 0.2
    fig, axes = plt.subplots(2, 1, figsize=(COL, 2.9), sharex=True)
    for ax, metric, ylabel in ((axes[0], "cer", "Mean CER (%)"), (axes[1], "field_em", "Field exact match (%)")):
        for k, (v, (label, col)) in enumerate(VARS.items()):
            m = np.array([stats[d][f"{metric}_{v}"][0] for d in DEGS]) * 100
            lo = np.array([stats[d][f"{metric}_{v}"][1] for d in DEGS]) * 100
            hi = np.array([stats[d][f"{metric}_{v}"][2] for d in DEGS]) * 100
            ax.bar(x + (k - 1.5) * w, m, w, color=col, label=label, edgecolor="white", linewidth=0.2)
            ax.errorbar(x + (k - 1.5) * w, m, yerr=[m - lo, hi - m], fmt="none", ecolor="#444B55", elinewidth=0.35,
                        capsize=0.8, capthick=0.35)
        ax.set_ylabel(ylabel, fontsize=7)
        ax.grid(axis="x", visible=False)
        ax.tick_params(labelsize=6.5)
    axes[1].set_ylim(0, 105)
    axes[1].set_xticks(x)
    axes[1].set_xticklabels([short[d] for d in DEGS], fontsize=6.5)
    h, l = axes[0].get_legend_handles_labels()
    l = ["Raw photo", "Rectified", "Rect. + Auto (gray)", "Rect. + B/W"]
    axes[0].legend(h, l, ncol=4, loc="upper center", bbox_to_anchor=(0.5, 1.2), frameon=False, fontsize=6.3,
                   handlelength=1.0, columnspacing=0.8, handletextpad=0.3)
    fig.tight_layout(h_pad=0.3)
    save(fig, "fig_cer_field_by_degradation.png")


def fig_threshold():
    page, cap = capture_for("shadow", 3, 2)
    rect = synth.warp_to_page(cap.image, cap.quad)
    g = cv2.cvtColor(rect, cv2.COLOR_BGR2GRAY)
    crop = (slice(560, 1360), slice(60, 1180))
    ims = [("Shadowed input", g),
           ("Global T = 127", cv2.threshold(g, 127, 255, cv2.THRESH_BINARY)[1]),
           ("Otsu", cv2.threshold(g, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)[1]),
           ("Adaptive 35/11", cv2.adaptiveThreshold(g, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 35, 11))]
    m = R["threshold"]["methods"]
    fig = plt.figure(figsize=(COL, 2.05))
    gs = fig.add_gridspec(2, 4, height_ratios=[0.9, 1.15])
    for k, (name, im) in enumerate(ims):
        ax = fig.add_subplot(gs[0, k])
        ax.imshow(im[crop], cmap="gray", vmin=0, vmax=255)
        ax.set_title(name, fontsize=7, pad=2)
        bare(ax)
    ax = fig.add_subplot(gs[1, :])
    methods = ["global127", "otsu", "adaptive"]
    xs = np.arange(3)
    a = [m[f"{k}_gray"]["mean_cer"] * 100 for k in methods]
    b = [m[f"{k}_clahe_median"]["mean_cer"] * 100 for k in methods]
    ax.bar(xs - 0.18, a, 0.36, color=GREY, label="On rectified gray")
    ax.bar(xs + 0.18, b, 0.36, color=BLUE, label="After CLAHE + median (app path)")
    for xi, (va, vb) in enumerate(zip(a, b)):
        ax.text(xi - 0.18, va + 0.6, f"{va:.2f}" if va < 1 else f"{va:.1f}", ha="center", va="bottom", fontsize=6.5)
        ax.text(xi + 0.18, vb + 0.6, f"{vb:.2f}" if vb < 1 else f"{vb:.1f}", ha="center", va="bottom", fontsize=6.5)
    ax.set_xticks(xs); ax.set_xticklabels(["Global T = 127", "Otsu", "Adaptive Gaussian 35/11"])
    ax.set_ylabel(f"Mean CER (%), n = {R['threshold']['n']}")
    ax.set_ylim(0, 74)
    ax.legend(frameon=False, loc="upper right")
    ax.grid(axis="x", visible=False)
    fig.tight_layout(h_pad=0.5, w_pad=0.2)
    save(fig, "fig_threshold_comparison.png")


def fig_dct():
    d = R["dct"]
    fig, (a1, a2) = plt.subplots(2, 1, figsize=(COL, 2.9))
    q = [r["quality"] for r in d["quantisation"]]
    a1.plot(q, [r["mean_psnr_db"] for r in d["quantisation"]], "-o", color=BLUE, lw=1, ms=2.5)
    a1.set_xlabel("Quality factor (8x8 DCT, scaled JPEG luminance table)", labelpad=1)
    a1.set_ylabel("PSNR (dB)", color=BLUE)
    a1.invert_xaxis()
    t1 = a1.twinx()
    t1.plot(q, [r["mean_cer"] * 100 for r in d["quantisation"]], "-s", color=RED, lw=1, ms=2.5)
    t1.set_ylabel("OCR CER (%)", color=RED)
    t1.spines["right"].set_visible(True); t1.grid(False)
    a1.set_title("(a) Quantisation", fontsize=7.5, pad=2)
    zon = [r for r in d["zonal"] if r["mean_psnr_db"] is not None]
    k = [r["coeff_fraction"] * 100 for r in zon]
    a2.plot(k, [r["mean_psnr_db"] for r in zon], "-o", color=BLUE, lw=1, ms=2.5)
    a2.set_xlabel("Low-frequency coefficients kept (% of 64)", labelpad=1)
    a2.set_ylabel("PSNR (dB)", color=BLUE)
    a2.invert_xaxis()
    t2 = a2.twinx()
    t2.plot(k, [r["mean_cer"] * 100 for r in zon], "-s", color=RED, lw=1, ms=2.5)
    t2.set_ylabel("OCR CER (%)", color=RED)
    t2.set_yscale("log")
    t2.spines["right"].set_visible(True); t2.grid(False)
    for r in zon:
        a2.annotate(r["keep"], (r["coeff_fraction"] * 100, r["mean_psnr_db"]), xytext=(2, 3),
                    textcoords="offset points", fontsize=6, color=BLUE)
    a2.set_xlim(60, -4)
    a2.set_title("(b) Zonal coding, keep k x k (log CER axis)", fontsize=7.5, pad=2)
    fig.tight_layout(h_pad=0.6)
    save(fig, "fig_dct_quality.png")


def fig_block_matching():
    rng = np.random.default_rng(bm.SEED)
    ref, cur, true_mv = bm.make_pair(0, rng)
    x, y = 16 * 10 + bm.P + bm.BLOCK, 16 * 14 + bm.P + bm.BLOCK
    while cur[y:y + 16, x:x + 16].std() < 25:
        x += 16
    fig, axes = plt.subplots(1, 2, figsize=(COL, 2.05))
    for ax, (name, fn) in zip(axes, [("(a) Three-step search", bm.three_step), ("(b) 2-D logarithmic search", bm.two_d_log)]):
        g = np.arange(-bm.P, bm.P + 1)
        gx, gy = np.meshgrid(g, g)
        ax.scatter(gx, gy, s=1.5, color="#C3C8CE", label="Exhaustive (225)")
        mv, _, order = fn(cur, ref, x, y)
        ox, oy = zip(*order)
        ax.plot(ox, oy, "-", color="#B5BBC3", lw=0.4, zorder=1)
        ax.scatter(ox, oy, c=np.arange(len(order)), cmap="viridis", s=9, edgecolor=INK, linewidths=0.3, zorder=3,
                   label=f"Visited ({len(set(order))})")
        ax.scatter([mv[0]], [mv[1]], marker="s", s=40, facecolor="none", edgecolor=RED, lw=0.8, zorder=4,
                   label=f"Chosen {mv}")
        ax.scatter([true_mv[0]], [true_mv[1]], marker="x", s=22, color=RED, lw=0.9, zorder=4, label=f"True {true_mv}")
        ax.set_xlim(-8, 8); ax.set_ylim(8, -8); ax.set_aspect("equal")
        ax.set_xticks([-7, 0, 7]); ax.set_yticks([-7, 0, 7])
        ax.set_xlabel("u (px)", labelpad=1); ax.set_ylabel("v (px)", labelpad=0)
        ax.set_title(name, fontsize=7.5, pad=2)
        ax.legend(loc="upper center", bbox_to_anchor=(0.5, -0.2), frameon=False, fontsize=6, ncol=2,
                  handletextpad=0.2, columnspacing=0.6, markerscale=0.9)
    fig.tight_layout(w_pad=0.6)
    save(fig, "fig_block_matching.png")


# ---------------------------------------------------------------- phone screenshots
def phone_strip(files, labels, name, crop_top=110, crop_bottom=2310, width=FULL, height=2.95):
    shots = []
    for f in files:
        im = Image.open(ROOT / "docs" / "screenshots" / f).convert("RGB")
        shots.append(im.crop((0, crop_top, im.width, crop_bottom)))
    fig, axes = plt.subplots(1, len(shots), figsize=(width, height))
    for ax, im, lab in zip(axes, shots, labels):
        ax.imshow(np.asarray(im))
        ax.set_xlabel(lab, fontsize=7, labelpad=2)
        bare(ax)
    fig.tight_layout(w_pad=0.3)
    save(fig, name)


def main():
    stats = bootstrap_stats()
    fig_architecture()
    if len(sys.argv) > 1 and sys.argv[1] == 'arch':
        return
    fig_pipeline()
    fig_histogram()
    fig_cer_field(stats)
    fig_threshold()
    # DCT results are reported as Table VII in the paper; fig_dct() remains available for slides.
    fig_block_matching()
    phone_strip(["03_crop.png", "03_preview_aligned.png", "05_certificate_fields.png"],
                ["(a) Manual crop", "(b) Aligned preview", "(c) Five fields"],
                "fig_app_screens.png", width=COL, height=2.2)
    phone_strip(["17_lab_geometry.png", "10b_lab_histogram_charts.png", "16c_lab_motion_search_points.png"],
                ["(a) Page geometry", "(b) Histogram, CLAHE", "(c) Block matching"],
                "fig_lab_screens.png", width=COL, height=2.2)

if __name__ == "__main__":
    main()
