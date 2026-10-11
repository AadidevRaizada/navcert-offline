"""Builds every figure in docs/figures/ from results.json, block_matching.json and regenerated synthetic data."""
from __future__ import annotations

import json
import sys
from pathlib import Path

import cv2
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import block_matching as bm  # noqa: E402
import pipeline as pl  # noqa: E402
import synth  # noqa: E402
from run_experiments import conditions, make_capture, dct_codec  # noqa: E402

OUT = HERE.parent.parent / "docs" / "figures"
OUT.mkdir(parents=True, exist_ok=True)
R = json.loads((HERE / "results.json").read_text())
BM = json.loads((HERE / "block_matching.json").read_text())
INK, MUTED, BLUE, GOLD, RED = "#0D1B2A", "#5B6472", "#1F4BD8", "#E0A800", "#D62828"
VAR_STYLE = {"raw": ("Raw photo", "#A3A9B2"), "rectified": ("Rectified", GOLD),
             "enhanced_gray": ("Rectified + enhanced (gray)", BLUE), "enhanced_bw": ("Rectified + B/W adaptive", INK)}
DEG_NAME = {"clean": "Clean", "tilt": "Tilt", "shadow": "Shadow", "low_light": "Low light",
            "overexposure": "Over-\nexposure", "gaussian_blur": "Gaussian\nblur", "motion_blur": "Motion\nblur",
            "noise": "Noise", "jpeg": "JPEG"}
plt.rcParams.update({
    "figure.facecolor": "white", "axes.facecolor": "white", "savefig.facecolor": "white",
    "font.family": "DejaVu Sans", "font.size": 16, "axes.titlesize": 18, "axes.labelsize": 17,
    "xtick.labelsize": 15, "ytick.labelsize": 15, "legend.fontsize": 15, "axes.edgecolor": MUTED,
    "axes.labelcolor": INK, "text.color": INK, "xtick.color": INK, "ytick.color": INK,
    "axes.spines.top": False, "axes.spines.right": False, "axes.grid": True, "grid.color": "#E3E6EA",
    "grid.linewidth": 0.8, "axes.axisbelow": True,
})
PAGES = [synth.render_page(1000 + i) for i in range(R["setup"]["pages"])]
PER = R["setup"]["captures_per_condition"]


def save(fig, name):
    fig.savefig(OUT / name, dpi=150, bbox_inches="tight", pad_inches=0.25)
    plt.close(fig)
    h, w = cv2.imread(str(OUT / name)).shape[:2]
    print(f"{name}: {w}x{h}")


def rgb(img):
    return cv2.cvtColor(img, cv2.COLOR_BGR2RGB) if img.ndim == 3 else img


def capture_for(deg, sev, j):
    ci = conditions().index((deg, sev))
    return make_capture(PAGES, ci, j, len(PAGES), PER)


def save_stage(name, img):
    im = img if img.ndim == 3 else cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
    scale = 1600 / im.shape[1]
    im = cv2.resize(im, (1600, int(im.shape[0] * scale)), interpolation=cv2.INTER_AREA if scale < 1 else cv2.INTER_CUBIC)
    from PIL import Image
    pil = Image.fromarray(cv2.cvtColor(im, cv2.COLOR_BGR2RGB))
    if img.ndim == 2:
        pil = pil.convert("L")                       # gray stages stay 8-bit gray
    else:
        pil = pil.quantize(256, method=Image.Quantize.MEDIANCUT)  # keeps the file small for decks
    pil.save(OUT / name, optimize=True)
    print(f"{name}: {im.shape[1]}x{im.shape[0]}")


# 1. pipeline strip + individual stage images ---------------------------------------------
def fig_pipeline():
    page, cap = capture_for("tilt", 2, 1)
    scan = pl.prepare(cap.image)
    edges, closed = pl.edge_map(scan.analysis)
    quad = scan.analysis.copy()
    contours, _ = cv2.findContours(closed, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    cv2.drawContours(quad, contours, -1, (180, 180, 180), 2)
    pts = scan.corners_analysis.astype(np.int32)
    cv2.polylines(quad, [pts], True, (216, 75, 31), 7)
    for p in pts:
        cv2.circle(quad, tuple(int(v) for v in p), 16, (44, 199, 255), -1)
        cv2.circle(quad, tuple(int(v) for v in p), 16, (42, 27, 13), 3)
    gray = pl.enhance_for_ocr(scan.rectified, "AUTO")
    bw = pl.enhance_for_ocr(scan.rectified, "BLACK_WHITE")
    stages = [("1  Camera photo", scan.normalized, "raw"), ("2  Canny 55/170 + 5x5 close", 255 - closed, "edges"),
              ("3  Largest convex quad", quad, "quad"), ("4  Perspective warp", scan.rectified, "warped"),
              ("5  CLAHE + median + unsharp", gray, "clahe"), ("6  Adaptive threshold 35/11", bw, "bw")]
    fig, axes = plt.subplots(1, 6, figsize=(24, 6.6))
    for ax, (title, im, key) in zip(axes, stages):
        ax.imshow(rgb(im), cmap="gray", vmin=0, vmax=255)
        ax.set_xlabel(title, fontsize=17, labelpad=10)
        ax.set_xticks([]); ax.set_yticks([]); ax.grid(False)
        for s in ax.spines.values():
            s.set_visible(True); s.set_color("#C9CDD3")
        save_stage(f"fig_stage_{key}.png", im)
    fig.tight_layout(w_pad=0.6)
    save(fig, "fig_pipeline_strip.png")


# 2. histogram before / after equalisation -------------------------------------------------
def fig_histogram():
    page, cap = capture_for("shadow", 2, 0)
    scan = pl.prepare(cap.image)
    g = cv2.cvtColor(scan.rectified, cv2.COLOR_BGR2GRAY)
    variants = [("Rectified gray", g, "#A3A9B2"), ("Global histogram equalisation", cv2.equalizeHist(g), GOLD),
                ("CLAHE clip 2.2, 8x8 tiles", cv2.createCLAHE(2.2, (8, 8)).apply(g), BLUE)]
    fig, axes = plt.subplots(2, 3, figsize=(20, 13), gridspec_kw={"height_ratios": [2.1, 1]})
    top = max(np.bincount(im.ravel(), minlength=256).max() / im.size for _, im, _ in variants) * 1.1
    for k, (name, im, col) in enumerate(variants):
        ax = axes[0, k]
        ax.imshow(im, cmap="gray", vmin=0, vmax=255)
        ax.set_title(name, pad=12)
        ax.set_xticks([]); ax.set_yticks([]); ax.grid(False)
        h = np.bincount(im.ravel(), minlength=256) / im.size
        axes[1, k].fill_between(np.arange(256), h, color=col, alpha=0.9, step="mid")
        axes[1, k].set_xlim(0, 255)
        axes[1, k].set_xlabel("Gray level")
        axes[1, k].set_ylim(0, top)
        if k == 0:
            axes[1, k].set_ylabel("Fraction of pixels")
    fig.tight_layout()
    save(fig, "fig_histogram_clahe.png")


# 3. CER / field match by degradation -----------------------------------------------------
def grouped(metric, ylabel, name, pct=True):
    rows = {r["degradation"]: r for r in R["main"]["by_degradation"]}
    degs = list(DEG_NAME)
    x = np.arange(len(degs))
    w = 0.2
    fig, ax = plt.subplots(figsize=(20, 8))
    for k, v in enumerate(VAR_STYLE):
        label, col = VAR_STYLE[v]
        vals = [rows[d][f"{metric}_{v}"] * (100 if pct else 1) for d in degs]
        b = ax.bar(x + (k - 1.5) * w, vals, w, label=label, color=col, edgecolor="white", linewidth=0.8)
    ax.set_xticks(x)
    ax.set_xticklabels([f"{DEG_NAME[d]}\n(n={rows[d]['n']})" for d in degs])
    ax.set_ylabel(ylabel)
    ax.legend(ncol=4, loc="upper center", bbox_to_anchor=(0.5, 1.12), frameon=False)
    ax.grid(axis="x", visible=False)
    fig.tight_layout()
    save(fig, name)


def fig_severity():
    rows = R["main"]["by_condition"]
    degs = [d for d in DEG_NAME if d != "clean"]
    fig, axes = plt.subplots(2, 4, figsize=(22, 10), sharey=False)
    for ax, d in zip(axes.ravel(), degs):
        for v, (label, col) in VAR_STYLE.items():
            ys = [next(r for r in rows if r["degradation"] == d and r["severity"] == s)[f"cer_{v}"] * 100 for s in (1, 2, 3)]
            ax.plot([1, 2, 3], ys, "-o", color=col, lw=3, ms=8, label=label)
        ax.set_title(DEG_NAME[d].replace("\n", ""))
        ax.set_xticks([1, 2, 3]); ax.set_xticklabels(["Mild", "Medium", "Strong"])
    for ax in axes[:, 0]:
        ax.set_ylabel("Mean CER (%)")
    h, l = axes[0, 0].get_legend_handles_labels()
    fig.legend(h, l, ncol=4, loc="upper center", bbox_to_anchor=(0.5, 1.04), frameon=False)
    fig.tight_layout()
    save(fig, "fig_cer_by_severity.png")


# 4. threshold comparison --------------------------------------------------------------
def fig_threshold():
    page, cap = capture_for("shadow", 3, 2)
    rect = synth.warp_to_page(cap.image, cap.quad)
    g = cv2.cvtColor(rect, cv2.COLOR_BGR2GRAY)
    crop = (slice(560, 1360), slice(60, 1180))
    ims = [("Input (shadowed, rectified)", g),
           ("Global T = 127", cv2.threshold(g, 127, 255, cv2.THRESH_BINARY)[1]),
           ("Otsu", cv2.threshold(g, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)[1]),
           ("Adaptive Gaussian 35 / 11", cv2.adaptiveThreshold(g, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                                                                cv2.THRESH_BINARY, 35, 11))]
    m = R["threshold"]["methods"]
    fig = plt.figure(figsize=(22, 13))
    gs = fig.add_gridspec(2, 4, height_ratios=[1.25, 1])
    for k, (name, im) in enumerate(ims):
        ax = fig.add_subplot(gs[0, k])
        ax.imshow(im[crop], cmap="gray", vmin=0, vmax=255)
        ax.set_title(name, pad=10)
        ax.set_xticks([]); ax.set_yticks([]); ax.grid(False)
    ax = fig.add_subplot(gs[1, :])
    methods = ["global127", "otsu", "adaptive"]
    labels = ["Global T = 127", "Otsu", "Adaptive 35 / 11"]
    x = np.arange(3)
    a = [m[f"{k}_gray"]["mean_cer"] * 100 for k in methods]
    b = [m[f"{k}_clahe_median"]["mean_cer"] * 100 for k in methods]
    ax.bar(x - 0.18, a, 0.36, color="#A3A9B2", label="On rectified gray")
    ax.bar(x + 0.18, b, 0.36, color=BLUE, label="After CLAHE + median (app path)")
    for xi, (va, vb) in enumerate(zip(a, b)):
        ax.text(xi - 0.18, va, f"{va:.1f}", ha="center", va="bottom", fontsize=15)
        ax.text(xi + 0.18, vb, f"{vb:.1f}", ha="center", va="bottom", fontsize=15)
    ax.axhline(m["gray_no_threshold"]["mean_cer"] * 100, color=GOLD, lw=2.5, ls="--",
               label="No threshold (gray)")
    ax.set_xticks(x); ax.set_xticklabels(labels)
    ax.set_ylabel(f"Mean CER (%), n={R['threshold']['n']}")
    ax.legend(frameon=False, loc="upper right")
    ax.grid(axis="x", visible=False)
    fig.tight_layout()
    save(fig, "fig_threshold_comparison.png")


# 5. DCT compression ---------------------------------------------------------------------
def fig_dct():
    d = R["dct"]
    fig, (a1, a2) = plt.subplots(1, 2, figsize=(20, 7.5))
    q = [r["quality"] for r in d["quantisation"]]
    a1.plot(q, [r["mean_psnr_db"] for r in d["quantisation"]], "-o", color=BLUE, lw=3, ms=8, label="PSNR")
    a1.set_xlabel("JPEG-style quality factor (8x8 DCT, IJG table)")
    a1.set_ylabel("PSNR (dB)", color=BLUE)
    a1.invert_xaxis()
    t1 = a1.twinx()
    t1.plot(q, [r["mean_cer"] * 100 for r in d["quantisation"]], "-s", color=RED, lw=3, ms=8, label="OCR CER")
    t1.set_ylabel("OCR CER (%)", color=RED)
    t1.spines["right"].set_visible(True); t1.grid(False)
    k = [r["coeff_fraction"] * 100 for r in d["zonal"]]
    a2.plot(k, [np.nan if r["mean_psnr_db"] is None else r["mean_psnr_db"] for r in d["zonal"]], "-o", color=BLUE, lw=3, ms=8)
    a2.set_xlabel("Low-frequency DCT coefficients kept (% of 64)")
    a2.set_ylabel("PSNR (dB)", color=BLUE)
    a2.invert_xaxis()
    t2 = a2.twinx()
    t2.plot(k, [r["mean_cer"] * 100 for r in d["zonal"]], "-s", color=RED, lw=3, ms=8)
    t2.set_ylabel("OCR CER (%)", color=RED)
    a2.annotate("100 %: lossless", (100, a2.get_ylim()[1]), xytext=(0, -24), textcoords="offset points",
                ha="center", fontsize=15, color=BLUE)
    t2.spines["right"].set_visible(True); t2.grid(False)
    a1.set_title("Quantisation")
    a2.set_title("Zonal coding (keep k x k)")
    fig.tight_layout(w_pad=4)
    save(fig, "fig_dct_quality.png")

    gray = cv2.cvtColor(PAGES[0].image, cv2.COLOR_BGR2GRAY)
    crop = (slice(700, 900), slice(120, 620))
    shows = [("Original", gray)] + [(f"Quality {qq}", dct_codec(gray, quality=qq)[0]) for qq in (50, 10, 2)] + \
            [("Keep 2x2 of 8x8", dct_codec(gray, keep=2)[0])]
    fig, axes = plt.subplots(1, 5, figsize=(26, 4.2))
    for ax, (name, im) in zip(axes, shows):
        ax.imshow(im[crop], cmap="gray", vmin=0, vmax=255, interpolation="nearest")
        ax.set_title(name)
        ax.set_xticks([]); ax.set_yticks([]); ax.grid(False)
    fig.tight_layout()
    save(fig, "fig_dct_crops.png")


# 6. block matching -----------------------------------------------------------------------
def fig_block_matching():
    rng = np.random.default_rng(bm.SEED)
    ref, cur, true_mv = bm.make_pair(0, rng)
    x, y = 16 * 10 + bm.P + bm.BLOCK, 16 * 14 + bm.P + bm.BLOCK
    while cur[y:y + 16, x:x + 16].std() < 25:  # pick a textured (text) block
        x += 16
    fig = plt.figure(figsize=(24, 9.5))
    gs = fig.add_gridspec(1, 3, width_ratios=[1, 1, 1.25])
    for k, (name, fn) in enumerate([("Three-step search", bm.three_step), ("2D-logarithmic search", bm.two_d_log)]):
        ax = fig.add_subplot(gs[0, k])
        g = np.arange(-bm.P, bm.P + 1)
        gx, gy = np.meshgrid(g, g)
        ax.scatter(gx, gy, s=40, color="#D5D9DE", label="Exhaustive (225 points)")
        mv, _, order = fn(cur, ref, x, y)
        ox, oy = zip(*order)
        ax.plot(ox, oy, "-", color="#C9CDD3", lw=1, zorder=1)
        ax.scatter(ox, oy, c=np.arange(len(order)), cmap="viridis", s=170, edgecolor=INK, zorder=3,
                   label=f"Visited ({len(set(order))} points)")
        ax.scatter([mv[0]], [mv[1]], marker="s", s=520, facecolor="none", edgecolor=RED, lw=3, zorder=4,
                   label=f"Chosen {mv}")
        ax.scatter([true_mv[0]], [true_mv[1]], marker="x", s=300, color=RED, lw=3, zorder=4,
                   label=f"True {true_mv}")
        ax.set_xlim(-8, 8); ax.set_ylim(8, -8); ax.set_aspect("equal")
        ax.set_xticks(range(-7, 8, 7)); ax.set_yticks(range(-7, 8, 7))
        ax.set_xlabel("u (px)"); ax.set_ylabel("v (px)")
        ax.set_title(name)
        ax.legend(loc="upper center", bbox_to_anchor=(0.5, -0.12), frameon=False, fontsize=14, ncol=2)
    ax = fig.add_subplot(gs[0, 2]); ax.axis("off")
    rows = []
    for cond, lab in (("sharp_frames", "sharp"), ("lowpass_sigma2_frames", "low-pass")):
        for m, mn in (("exhaustive", "Exhaustive"), ("three_step", "Three-step"), ("2d_log", "2D-log")):
            r = BM[cond]["methods"][m]
            rows.append([f"{mn} ({lab})", f"{r['mean_search_points_per_block']:.1f}",
                         f"{100 * r['exact_vector_rate_textured']:.1f}%", f"{r['mean_vector_error_px_textured']:.2f}",
                         f"{r['mean_mad_cost']:.2f}"])
    t = ax.table(cellText=rows, colLabels=["Method (frames)", "Points /\nblock", "Exact MV\n(textured)",
                                           "Mean MV\nerror (px)", "Mean\nMAD"],
                 loc="center", cellLoc="center", colWidths=[0.36, 0.15, 0.17, 0.17, 0.13])
    t.auto_set_font_size(False); t.set_fontsize(15); t.scale(1, 3.1)
    for (r_, c_), cell in t.get_celld().items():
        cell.set_edgecolor("#D5D9DE")
        if r_ == 0:
            cell.set_facecolor("#EEF1F5"); cell.set_text_props(weight="bold")
    s = BM["sharp_frames"]
    ax.set_title(f"{s['pairs']} frame pairs, {s['blocks_total']} blocks of {s['block']}x{s['block']}, p = {s['search_range']}",
                 fontsize=16, y=0.84)
    fig.tight_layout()
    save(fig, "fig_block_matching.png")


# 7. quality check confusion, 8. page detection ---------------------------------------------
def fig_quality():
    q = R["main"]["quality_check"]
    labels = q["labels"]
    m = np.array([[q["confusion_expected_x_predicted"][e][p] for p in labels] for e in labels])
    fig, ax = plt.subplots(figsize=(14, 10))
    ax.imshow(m, cmap="Blues")
    ax.grid(False)
    names = ["Good", "Too dark", "Too bright", "Soft image"]
    ax.set_xticks(range(4)); ax.set_xticklabels(names)
    ax.set_yticks(range(4)); ax.set_yticklabels([f"{n}\n(n={m[i].sum()})" for i, n in enumerate(names)])
    ax.set_xlabel("App quality label (mean < 55, mean > 220, Laplacian var < 65)")
    ax.set_ylabel("Expected from the applied degradation")
    for i in range(4):
        for j in range(4):
            ax.text(j, i, str(m[i, j]), ha="center", va="center", fontsize=20,
                    color="white" if m[i, j] > m.max() * 0.55 else INK)
    for s in ax.spines.values():
        s.set_visible(False)
    fig.tight_layout()
    save(fig, "fig_quality_confusion.png")


def fig_detection():
    rows = {r["degradation"]: r for r in R["main"]["by_degradation"]}
    degs = list(DEG_NAME)
    x = np.arange(len(degs))
    fig, ax = plt.subplots(figsize=(20, 7.5))
    ax.bar(x - 0.2, [rows[d]["detection_rate"] * 100 for d in degs], 0.4, color="#A3A9B2", label="Quad found")
    ax.bar(x + 0.2, [rows[d]["iou_ge_0_9_rate"] * 100 for d in degs], 0.4, color=BLUE, label="IoU with true page >= 0.9")
    ax.set_xticks(x); ax.set_xticklabels([f"{DEG_NAME[d]}\n(n={rows[d]['n']})" for d in degs])
    ax.set_ylabel("Captures (%)"); ax.set_ylim(0, 105)
    ax.legend(ncol=2, loc="upper center", bbox_to_anchor=(0.5, 1.12), frameon=False)
    ax.grid(axis="x", visible=False)
    fig.tight_layout()
    save(fig, "fig_page_detection.png")


if __name__ == "__main__":
    fig_pipeline()
    fig_histogram()
    grouped("cer", "Mean character error rate (%)", "fig_cer_by_degradation.png")
    grouped("field_em", "Five-field exact match (%)", "fig_field_match_by_degradation.png")
    fig_severity()
    fig_threshold()
    fig_dct()
    fig_block_matching()
    fig_quality()
    fig_detection()
