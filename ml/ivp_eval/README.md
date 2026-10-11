# IVP evaluation: paired raw vs preprocessed OCR on synthetic certificates

This folder measures what the NavCert Offline preprocessing actually buys. It answers the main gap in the first paper draft: there was no paired raw-versus-enhanced evaluation, so no numerical preprocessing gain could be claimed.

Every page is synthetic. The generator renders certificate-like pages with fictional organisations, people and numbers, so the true text, the five field values and the true page corners are known exactly. No real certificate image is used.

## What is measured

| Exp. | Question | Script |
| --- | --- | --- |
| a | Does page detection find the true page? (detection rate, IoU, corner error) | `run_experiments.py` |
| b | OCR CER / WER / order-free word recall / five-field exact match for raw photo, rectified, rectified + enhanced (gray, `AUTO`) and B/W adaptive mode | `run_experiments.py` |
| c | Do the app's dark / bright / blur warnings fire on the images they should? | `run_experiments.py` |
| d | Global fixed vs Otsu vs adaptive thresholding under uneven illumination | `run_experiments.py` |
| e | Runtime per stage (desktop CPU) | `run_experiments.py` |
| f | 8x8 DCT compression: PSNR and OCR CER vs quality factor and kept coefficients | `run_experiments.py` |
| g | Block matching: exhaustive vs three-step vs 2D-log search on frame pairs with known shake | `block_matching.py` |

## Method

**Pipeline port.** `pipeline.py` is a line-by-line Python port of `app/src/main/java/com/MAD/n152/DocumentScanner.kt`. OCR input is capped at 3200 px and analysis at 1600 px (bilinear). Quality: mean gray < 55 is "Too dark", > 220 is "Too bright", Laplacian variance < 65 is "Soft image", checked in that order. Detection: gray, Gaussian 5x5, Canny 55/170, 5x5 rectangular close, `RETR_LIST` contours. The 20 largest contours are approximated with `approxPolyDP` at 2% of perimeter. A convex quad covering at least 15% of the frame qualifies, and the largest wins. The warp is cubic with replicated borders. If no quad is found, the full frame is used, as in the app. Enhancement: CLAHE clip 2.2 with 8x8 tiles, then a 3x3 median. `AUTO` mode adds unsharp masking (sigma 1.05, amount 1.32). B/W mode uses an adaptive Gaussian threshold (block 35, C 11). Both modes add an 18 px white border.

**Synthetic pages** (`synth.py`). There are 40 A4 pages at 1240x1754 px, seeds 1000 to 1039. Each draws a body font from 12 Windows fonts (Times, Georgia, Arial, Calibri, Verdana, Palatino, Bookman, Tahoma, Trebuchet, Segoe UI, Cambria Bold, Garamond). Each page has a header, a certificate title, a wrapped body paragraph and seven labelled fields: certificate name, holder, certificate no., date of issue, place of issue, issuing authority and date of expiry. Dates use three formats. Each page also carries a decorative double border (a deliberate quad distractor), a semi-transparent coloured seal, a pen-stroke signature and a footer saying the document is synthetic.

**Camera simulation.** Each page is warped into a 1800x2400 frame on a textured background (wood, laminate or fabric) with clutter, a soft drop shadow and vignetting. Every capture gets base corner jitter of 3% of frame width, sensor noise of sigma 2 and JPEG quality 92. One degradation is then applied at three severities:

| Degradation | Mild / medium / strong |
| --- | --- |
| tilt (corner jitter, fraction of frame width; rotation up to 100x that in degrees) | 0.06 / 0.10 / 0.14 |
| shadow (cast-shadow factor d; light gradient down to (1+d)/2) | 0.65 / 0.45 / 0.30 |
| low light (exposure gain, gamma 1.1, noise sigma 6/8/10) | 0.40 / 0.25 / 0.15 |
| overexposure (gain, plus offset 25/50/75, clipped) | 1.45 / 1.80 / 2.20 |
| Gaussian blur (sigma, px) | 1.2 / 2.0 / 3.0 |
| motion blur (kernel length, px, random angle) | 7 / 13 / 19 |
| sensor noise (sigma, 8-bit levels) | 10 / 20 / 32 |
| JPEG quality | 25 / 12 / 6 |

There are 25 conditions (clean plus 8 x 3). Each condition uses 8 captures, which gives n = 200 captures (8 clean, 24 per degradation). Condition c, capture j uses page `(8c + j) mod 40` and capture seed `100000 + 100c + j`.

**OCR engine.** PaddleOCR 3.7.0 runs PP-OCRv6_small_det and PP-OCRv6_small_rec through ONNX Runtime 1.30.0 on the CPU, with orientation, unwarping and text-line orientation disabled. These are the same settings as `ml/bootstrap_ocr.py`, with models from the local cache. OpenCV is 4.10.0. Recognised boxes are grouped into lines by vertical overlap and read left to right.

**Metrics.** Text is normalised by lower-casing, unifying quotes and dashes, and collapsing all whitespace and line breaks to one space. CER is the character Levenshtein distance divided by reference length. WER is the same over word tokens. Both include reading-order errors. Word recall ignores order: it is the fraction of reference words, as a multiset with punctuation stripped, that appear in the output. Field exact match uses a simple rule. Take the first OCR line containing the label ("Certificate Name", "Date of Issue", "Place of Issue", "Issuing Authority", "Date of Expiry"), ignoring case, spaces and punctuation. Then compare the rest of the line with the true value under the same normalisation. Page IoU is computed with `cv2.intersectConvexConvex`. Corner error is the mean Euclidean distance in frame pixels.

**Threshold study (d).** All 24 shadow captures are rectified with the true corners to the exact page raster, so thresholding is isolated from detection. Three thresholds are compared: global T = 127, Otsu, and adaptive Gaussian 35/11. Each is applied to the plain gray image and to the CLAHE + median image (the app path). The study reports OCR CER and pixel F1 against the rendered ink mask.

**DCT study (f).** Six clean pages are converted to gray and passed through an 8x8 block DCT. Two codecs are tested: (i) quantisation with the standard JPEG luminance table, scaled by the IJG quality formula; (ii) zonal coding that keeps the top-left k x k coefficients. Real OpenCV JPEG bits per pixel are reported for the same quality factors.

**Block matching (g).** The study uses 10 frame pairs at 480x640 gray from synthetic captures. Frame 2 is frame 1 shifted by a random integer vector in [-7, 7]^2 plus noise of sigma 2, so every block has a known true vector. Blocks are 16x16, the search range is p = 7, and the cost is mean absolute difference. There are 999 blocks per pair (9,990 in total). A block counts as textured if its standard deviation is above 10. The study is repeated with both frames low-passed (Gaussian sigma 2).

## Results (n = 200 captures unless stated)

**Overall, paired on the same 200 captures:**

| Variant | Mean CER | Median CER | Mean WER | Word recall | Five-field exact match |
| --- | --- | --- | --- | --- | --- |
| Raw photo | 9.6% | 0.49% | 14.1% | 92.9% | 691 / 1000 (69.1%) |
| Rectified | 4.2% | 0.10% | 8.7% | 92.4% | 884 / 1000 (88.4%) |
| Rectified + enhanced (gray, AUTO) | 4.2% | 0.10% | 7.4% | 93.6% | 884 / 1000 (88.4%) |
| Rectified + B/W adaptive | 7.6% | 0.58% | 14.6% | 86.7% | 740 / 1000 (74.0%) |

- **Paired CER reduction, raw to enhanced gray:** mean 5.4 percentage points, bootstrap 95% CI 3.9 to 7.1. Enhanced was better on 112 captures, worse on 40 and tied on 48.
- **Page detection:** a quad was found on 184/200 captures. The quad had IoU >= 0.9 on 156/200. Mean IoU when detected was 0.976. Median corner error was 1.3 px in a 1800x2400 frame.
- **Quality check:** 150/200 captures (75%) got the expected label. Confusion counts:

| Expected label \ app label | Good | Too dark | Too bright | Soft image |
| --- | --- | --- | --- | --- |
| Good (n = 104) | 104 | 0 | 0 | 0 |
| Too dark (n = 24) | 8 | 16 | 0 | 0 |
| Too bright (n = 24) | 5 | 0 | 19 | 0 |
| Soft image (n = 48) | 37 | 0 | 0 | 11 |

The table has no false alarms. The dark flag fires on every medium and strong low-light capture. All 8 missed dark captures are mild ones (mean gray 65). The blur flag catches strong Gaussian blur (8/8) but almost never motion blur (1/24). Motion blur keeps a high Laplacian variance along the stroke direction.

**By degradation:** mean CER per variant, five-field exact match per variant, and word recall (raw / enhanced gray):

| Degradation | n | Quad found | IoU >= 0.9 | CER raw | CER rect | CER gray | CER B/W | Field raw | Field rect | Field gray | Field B/W | Word recall raw / gray |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| clean | 8 | 100% | 100% | 3.3 | 0.1 | 0.0 | 0.1 | 67.5 | 100.0 | 100.0 | 97.5 | 99.0 / 99.7 |
| tilt | 24 | 100% | 100% | 20.3 | 0.5 | 0.4 | 0.4 | 29.2 | 97.5 | 98.3 | 93.3 | 99.4 / 99.6 |
| shadow | 24 | 100% | 100% | 1.9 | 0.0 | 0.0 | 0.1 | 92.5 | 99.2 | 100.0 | 98.3 | 99.6 / 99.8 |
| low light | 24 | 33% | 29% | 8.2 | 4.9 | 3.5 | 2.6 | 72.5 | 83.3 | 85.8 | 87.5 | 98.3 / 98.9 |
| overexposure | 24 | 100% | 46% | 2.5 | 1.1 | 2.7 | 2.1 | 79.2 | 97.5 | 93.3 | 80.8 | 98.1 / 95.5 |
| Gaussian blur | 24 | 100% | 96% | 6.6 | 3.0 | 3.0 | 8.5 | 89.2 | 99.2 | 96.7 | 32.5 | 90.8 / 96.3 |
| motion blur | 24 | 100% | 46% | 30.3 | 24.4 | 24.6 | 41.5 | 32.5 | 35.0 | 35.8 | 20.0 | 57.3 / 57.9 |
| noise | 24 | 100% | 100% | 5.2 | 0.8 | 0.9 | 7.4 | 78.3 | 92.5 | 94.2 | 84.2 | 99.7 / 99.7 |
| JPEG | 24 | 100% | 100% | 3.9 | 0.3 | 0.3 | 0.7 | 80.0 | 99.2 | 99.2 | 87.5 | 97.7 / 99.2 |

Per-severity values are in `results.json` under `main.by_condition`, and per-capture rows are in `results.csv`.

**Threshold comparison (n = 24 shadow captures, true-corner rectification):**

| Method | CER on gray | Ink F1 on gray | CER after CLAHE + median | Ink F1 after CLAHE + median |
| --- | --- | --- | --- | --- |
| Global T = 127 | 34.2% | 0.41 | 33.6% | 0.30 |
| Otsu | 39.2% | 0.25 | 40.2% | 0.22 |
| Adaptive Gaussian 35/11 | 0.05% | 0.91 | 0.40% | 0.87 |
| No threshold (gray) | 0.03% | n/a | n/a | n/a |

**DCT compression (6 pages):** the uncompressed CER is 0.02%.

| Quality | 90 | 75 | 50 | 30 | 20 | 10 | 5 | 2 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PSNR (dB) | 46.0 | 39.0 | 34.9 | 32.3 | 30.3 | 27.7 | 25.4 | 23.6 |
| Nonzero coefficients | 12.7% | 10.1% | 8.1% | 6.8% | 6.0% | 4.7% | 3.8% | 3.1% |
| OCR CER | 0.11% | 0.41% | 0.33% | 0.28% | 0.34% | 0.35% | 0.48% | 1.21% |
| OpenCV JPEG bits/pixel | 0.99 | 0.72 | 0.56 | 0.45 | 0.38 | 0.29 | 0.23 | 0.19 |

| Zonal keep | 8x8 | 6x6 | 4x4 | 3x3 | 2x2 | 1x1 |
| --- | --- | --- | --- | --- | --- | --- |
| Coefficients kept | 100% | 56.3% | 25% | 14.1% | 6.3% | 1.6% |
| PSNR (dB) | lossless | 30.3 | 24.2 | 21.4 | 19.0 | 16.6 |
| OCR CER | 0.02% | 0.12% | 0.30% | 0.75% | 4.1% | 98.9% |

**Block matching (9,990 blocks; 5,221 textured on sharp frames, 5,097 on low-pass frames):**

| Frames | Method | Points / block | Exact vector (textured) | Mean vector error, textured (px) | Mean MAD |
| --- | --- | --- | --- | --- | --- |
| sharp | exhaustive | 225 | 99.9% | 0.00 | 1.60 |
| sharp | three-step | 25 | 29.6% | 5.05 | 6.59 |
| sharp | 2D-log | 20.7 (max 31) | 32.8% | 5.10 | 6.82 |
| low-pass sigma 2 | exhaustive | 225 | 92.9% | 0.10 | 0.22 |
| low-pass sigma 2 | three-step | 25 | 49.1% | 3.22 | 0.82 |
| low-pass sigma 2 | 2D-log | 21.5 (max 31) | 58.7% | 2.96 | 0.72 |

**Runtime** (median ms, AMD Ryzen 9 8945H, Python 3.12 + OpenCV 4.10, 12 clean 1800x2400 captures x 5 repeats): resize 1.3, quality check 14.6, page detection 6.3, perspective warp 8.8, enhance gray 6.2, enhance B/W 19.1, PP-OCRv6 small OCR 1063 (12 calls). These are desktop timings of the Python port, not phone timings.

## What the numbers say

1. **Rectification is the main gain.** Mean CER falls from 9.6% to 4.2% and five-field exact match rises from 69.1% to 88.4% on the same 200 captures. Word recall barely moves (92.9% to 93.6%). So the gain comes mostly from restoring line geometry and reading order: labels and values land on the same line again. It is not mostly better glyph recognition. This matters directly for label-based field extraction.
2. **The gray enhancement adds little on average for this OCR engine, but helps where detection fails or text is soft.** Low light: when no quad is found, the strong-severity CER drops from 11.1% (raw, which equals rectified) to 7.2% with gray enhancement and to 3.8% with B/W. Gaussian blur: word recall rises from 90.8% to 96.3%. Enhancement hurts on overexposure (CER 1.1% to 2.7%).
3. **B/W adaptive mode is a tool for uneven light, not a default.** It wins on low light, but it hurts on blur, noise and strong motion blur, where binarisation breaks thin strokes.
4. **Under a cast shadow, adaptive thresholding is the only binariser that works.** Global and Otsu thresholds black out the shadowed half (CER 34% to 40%). Adaptive 35/11 keeps CER under 0.5%.
5. **Text survives aggressive DCT compression.** At quality 10 (4.7% of coefficients nonzero, 0.29 bits/pixel), PSNR drops to 27.7 dB while CER stays at 0.35%. Zonal coding breaks down only below 3x3.
6. **Fast block-matching searches assume a smooth error surface, and text breaks that assumption.** On sharp text frames, TSS and 2D-log use about 9 to 11 times fewer points than exhaustive search but recover the exact vector on only about 30% of textured blocks. Low-pass pre-filtering raises this to 49% (TSS) and 59% (2D-log).

## Reproduce

```powershell
ml\.venv\Scripts\python.exe -m pip install matplotlib rapidfuzz   # cv2, numpy, paddleocr, onnxruntime already present
ml\.venv\Scripts\python.exe ml\ivp_eval\block_matching.py       # ~2 min -> block_matching.json
ml\.venv\Scripts\python.exe ml\ivp_eval\run_experiments.py      # ~30 min on the CPU above -> results.json, results.csv
ml\.venv\Scripts\python.exe ml\ivp_eval\make_figures.py         # -> docs/figures/fig_*.png
```

The synthetic images are regenerated from seeds and never stored. `data/` (ground-truth JSON and the run log) and `cache/` (OCR results keyed by image hash, so re-runs are fast) are git-ignored. The PP-OCRv6 models must already be in `~/.paddlex/official_models`. `PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True` keeps the run offline.

## Figures (`docs/figures/`)

| File | Content |
| --- | --- |
| `fig_pipeline_strip.png` | Raw photo, closed Canny edges, contours and the detected quad, warp, CLAHE + median + unsharp, adaptive B/W |
| `fig_stage_{raw,edges,quad,warped,clahe,bw}.png` | The same six stages as individual 1600 px images |
| `fig_histogram_clahe.png` | Shadowed page: gray vs global histogram equalisation vs CLAHE, with histograms |
| `fig_cer_by_degradation.png` | Mean CER by degradation for the four variants |
| `fig_field_match_by_degradation.png` | Five-field exact match by degradation for the four variants |
| `fig_cer_by_severity.png` | CER vs severity for each degradation |
| `fig_page_detection.png` | Quad found and IoU >= 0.9 rates by degradation |
| `fig_quality_confusion.png` | Expected vs app quality label |
| `fig_threshold_comparison.png` | Global vs Otsu vs adaptive on a shadowed page, with CER bars |
| `fig_dct_quality.png`, `fig_dct_crops.png` | PSNR and CER vs quality factor and kept coefficients, with crops |
| `fig_block_matching.png` | TSS and 2D-log search points on the +/-7 grid, plus the cost table |

## Limitations

- **Synthetic data is not real certificates.** The pages are clean vector renderings with a single-column layout and known fonts. The camera model is a simple composition of warp, illumination, blur, noise and JPEG. Real captures add lens distortion, paper curl, glare, handwriting, multi-column tables, stamps over text and low-resolution scans. The absolute numbers are therefore optimistic. The paired differences between variants are the meaningful result.
- **This is not the on-device OCR engine.** OCR here is PP-OCRv6 small on a desktop CPU. The app uses ML Kit Text Recognition v2 for print. A strong learned detector such as PP-OCR tolerates moderate tilt and contrast loss on its own, so the gain from enhancement may be larger or smaller with ML Kit. The same experiment should be repeated on the phone.
- **The field rule is deliberately simple.** It is not the app's hybrid extractor (rules, then MiniLM, then MobileBERT). It measures whether the OCR text preserves label-value structure.
- **The sample is small.** There are 8 captures per condition and 24 per degradation, with 40 page designs. Per-condition means carry wide uncertainty. Only the overall paired comparison has a bootstrap interval.
- **Thresholds were not tuned.** The severities were fixed before the run, and the app thresholds were used exactly as shipped. The blur check (Laplacian variance < 65) misses motion blur at this frame size, and it is measured on the whole frame including the background.
