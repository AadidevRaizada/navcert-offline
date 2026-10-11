# NavCert Offline — Image & Video Processing (IVP) pack

Course: Image and Video Processing, B.Tech, SVKM's NMIMS MPSTME Mumbai, AY 2026-27.
Student: Aadidev Raizada · N152. Presentation: about 10 minutes plus Q&A.

This file has four parts:

1. Syllabus coverage matrix (Units 1–8, CO1–CO5).
2. Viva prep: 20 likely questions with short answers.
3. The IVP deck plan: 15 slides, 600 seconds, with speaker notes.
4. Code-versus-paper contradictions for the paper writer.

Source of truth for every number below: `app/src/main/java/com/MAD/n152/DocumentScanner.kt`, `PaddleHandwritingRecognizer.kt`, `DocumentTextFormatter.kt`, `DocumentRepository.kt`, `MainActivity.kt`, `ml/README.md`, and `ml/ivp_eval/results.json` once it exists. Formula-level facts (OpenCV defaults, search-point counts) are standard textbook or library behaviour and are derived, not measured.

---

## 0. Asset contract (file names the deck expects)

Other workstreams drop files into these paths. The deck slots them in by name. Section 3 says which slide uses which file. If a file is missing on the day, the slide uses its x-embed animation alone. Section 3.0 lists what existed when this plan was checked.

**App screenshots — `docs/screenshots/`** (synthetic certificate only, from `app/src/main/assets/lab/`)

| File | Shows |
|---|---|
| `app_home.png` | Camera-first home screen |
| `app_preview_aligned.png` | Preview after `DocumentScanner.prepare`, status "Page aligned", quality chip |
| `app_result_fields.png` | Extracted text plus the five certificate fields |
| `lab_01_resolution.png` | Pipeline Lab: spatial resolution + gray-level quantization |
| `lab_02_point.png` | Negative, contrast stretch, log, gamma, intensity slicing |
| `lab_03_histogram.png` | Histogram, global HE, CLAHE |
| `lab_04_spatial_frequency.png` | Mean / Gaussian / median / sharpening + DFT spectrum + frequency low-pass |
| `lab_05_dct.png` | 8×8 DCT compression with PSNR |
| `lab_06_edges.png` | Sobel, Laplacian, LoG, Canny |
| `lab_07_morphology.png` | Dilate, erode, open, close, boundary |
| `lab_08_threshold.png` | Global vs Otsu vs adaptive + connected components (4/8) |
| `lab_09_colour.png` | RGB / HSV / YUV channels + colour slicing of the stamp |
| `lab_10_geometry.png` | Contour → quad → perspective warp |
| `lab_11_motion.png` | Block matching: exhaustive vs three-step vs 2-D log |

**Figures — `docs/figures/`** (PNG, ≥1600 px wide, white background)

| File | Shows |
|---|---|
| `fig_pipeline.png` | The capture → OCR pipeline diagram |
| `fig_cer_stages.png` | OCR CER: raw vs rectified vs enhanced (synthetic benchmark) |
| `fig_threshold_compare.png` | Otsu vs adaptive threshold under uneven light (CER or foreground error) |
| `fig_dct_quality_cer.png` | DCT/JPEG quality factor vs PSNR and CER |
| `fig_block_matching_cost.png` | Search points and SAD cost: ES vs TSS vs 2-D log |

**Numbers — `ml/ivp_eval/results.json`.** The evidence slide quotes only values read from this file, with the synthetic-benchmark caveat.

---

## 1. Syllabus coverage matrix

Legend: **App** = runs in the production scan path. **Lab** = runs on the user's own scan in the in-app Pipeline Lab screen. **Paper** = discussed in the IEEE paper. "Not built" is said openly; examiners respect a clean boundary more than a stretched claim.

**Pipeline Lab code anchors (`app/src/main/java/com/MAD/n152/IvpOperations.kt`, tests in `app/src/test/java/com/MAD/n152/IvpOperationsTest.kt`).** These are hand-written textbook implementations on a `GrayImage` matrix, so the examiner can read the algorithm, not just an OpenCV call:

| Syllabus item | Function |
|---|---|
| Gray-level / spatial resolution | `requantize(image, levels)`, `downsampleReplicate(image, factor)` |
| Distance measures (Euclidean, D4, D8) | `euclideanDistance`, `cityBlockDistance`, `chessboardDistance` |
| Point processing | `negativeLut`, `contrastStretchLut`, `logLut`, `gammaLut`, `intensitySliceLut`, `threshold` |
| Histogram, global HE | `histogram`, `equalize`, `entropy` |
| Otsu | `otsuThreshold(hist)` (maximises between-class variance) |
| Component labelling 4 / 8 | `connectedComponents(binary, connectivity)` |
| Morphology | `erode`, `dilate`, `boundary` (A − A ⊖ B) |
| DCT compression | `dct8x8`, `idct8x8`, `scaledQuantTable(quality)`, `dctQuantize`, `dctTopK`, `dctMagnitudeImage`, `mse`, `psnr` |
| Motion estimation | `sad`, `exhaustiveSearch`, `threeStepSearch`, `twoDLogSearch` (Jain & Jain, initial step 4 for p = 7), `estimateMotion`, `motionCompensate`, `meanAbsoluteDifference` |

CLAHE, spatial filters, DFT, Sobel / Laplacian / LoG / Canny, adaptive threshold, colour conversion and the contour → quad → warp stage reuse OpenCV and the production functions in `DocumentScanner.kt` (`detectCorners`, `measureQuality`, `enhanceForOcr`, `warpPerspective`, now `internal` so the Lab can call them). The synthetic benchmark in `ml/ivp_eval/pipeline.py` is a Python port of `DocumentScanner.kt` with the same constants.

### Unit 1 — Image fundamentals (CO1)

| Topic | Where it is demonstrated | One-sentence viva line |
|---|---|---|
| Image as a matrix / function | App: `Utils.bitmapToMat` in `DocumentScanner.prepare` turns the ARGB_8888 bitmap into an OpenCV `Mat`; every later stage is an operation on f(x, y). | "From the moment the photo lands, the page is a matrix f(x, y) and every stage is a function on that matrix." |
| Colour image basics | App: input is 4-channel RGBA (8 bits per channel); `Imgproc.cvtColor(..., COLOR_RGBA2GRAY)` in `detectCorners`, `measureQuality`, `enhanceForOcr`. | "We read a 32-bit RGBA pixel but process an 8-bit luminance channel, Y = 0.299R + 0.587G + 0.114B." |
| Spatial resolution | App: `MAX_ANALYSIS_DIMENSION = 1600` (detection and quality), `MAX_OCR_DIMENSION = 3200` (OCR), preview 1200 px (`createPreviewBitmap`), handwriting line height 48 px (`PaddleHandwritingRecognizer.INPUT_HEIGHT`). Lab stage 1: resolution sweep. | "We pick resolution per task: 1600 px is enough to find a page border, but text strokes need up to 3200 px." |
| Gray-level resolution | Lab stage 1: quantization to fewer gray levels (false contouring). App works at 8 bits (256 levels). | "Dropping gray levels shows false contours long before text becomes unreadable, so 8 bits is plenty for OCR." |
| Distance measures | App: Euclidean `distance()` (`hypot`) sets the warp output width and height from the corner pairs; corner ordering uses x + y and y − x (`orderCorners`); `polygonArea` (shoelace) ranks candidate quads. m-path: see Unit 6. | "The rectified page size is the Euclidean length of its longest opposite edges." |
| File formats | App: camera / gallery input is usually JPEG; stored previews are JPEG quality 88 (`DocumentRepository`, `preview.compress(JPEG, 88)`); crop editor saves JPEG; Lab assets and paper figures are PNG (lossless). TIFF is not used. | "JPEG for storage size, PNG where every pixel must survive; TIFF suits multi-page archival scans, which we don't produce." |

### Unit 2 — Image enhancement (CO2)

| Topic | Where | Viva line |
|---|---|---|
| Digital negative, contrast stretching, log, power-law (gamma), intensity-level slicing | Lab stage 2 on the user's scan. App: PP-OCRv5 input normalisation s = r/127.5 − 1 (`bitmapToNchw`) is a linear point transform. | "Point processing maps each gray level independently; our recogniser itself expects the linear map r/127.5 − 1." |
| Thresholding (point op) | App: B&W mode (`adaptiveThreshold`, block 35, C 11). Lab stage 8: global and Otsu. | "B&W mode is a threshold, but a local one, because a single global value fails under shadow." |
| Smoothing spatial filters | App: 5×5 Gaussian before Canny (`GaussianBlur(gray, Size(5,5), 0)`, σ derived by OpenCV ≈ 1.1). Lab stage 4: mean and Gaussian. | "We smooth before Canny so the derivative responds to the page border, not paper grain." |
| Median filter | App: `medianBlur(contrast, denoised, 3)` (3×3) in `enhanceForOcr`. Lab stage 4. | "A 3×3 median removes speckle without smearing stroke edges." |
| Sharpening | App: unsharp masking `addWeighted(I, a, G_σ(I), 1 − a)` = I + (a − 1)(I − G_σ); Auto σ 1.05, a 1.32; Clean σ 1.35, a 1.50. Lab stage 4: Laplacian sharpening. | "We sharpen by subtracting a blurred copy: the difference is the edge detail, and we add 32 to 50 percent of it back." |
| Histogram equalisation | App: CLAHE clip 2.2, 8×8 tiles (`Imgproc.createCLAHE(2.2, Size(8.0, 8.0))`). Lab stage 3: histogram, global HE, CLAHE side by side. | "Global HE uses one mapping for the whole page; CLAHE uses 64 clipped mappings, so a shadow in one corner does not wreck the rest." |
| Fourier spectrum, frequency-domain filtering | Lab stage 4: log-magnitude DFT spectrum and ideal / Gaussian low-pass on the scan. App filters in the spatial domain. | "Text lives in high frequencies; the low-pass demo shows exactly what the median and Gaussian are removing." |

### Unit 3 — Image transforms (CO2)

| Topic | Where | Viva line |
|---|---|---|
| DFT | Lab stage 4 (spectrum, centred, log scale). | "Line spacing of the text shows up as bright vertical peaks in the spectrum." |
| DCT for compression | Lab stage 5: 8×8 block DCT, coefficient quantisation, PSNR. App: stored previews are JPEG q88, which is 8×8 DCT coding. Benchmark: `fig_dct_quality_cer.png`. | "Every saved preview is DCT-coded; the Lab shows how few coefficients a block of text actually needs." |
| Hadamard transform | Not built. | "Walsh–Hadamard uses ±1 basis functions, so it needs only additions; it compacts energy less well than the DCT, which is why JPEG uses the DCT." |

### Unit 4 — Morphological image processing (CO3)

| Topic | Where | Viva line |
|---|---|---|
| Dilation, erosion, opening, closing | App: `morphologyEx(edges, closed, MORPH_CLOSE, 5×5 rect)` in `detectCorners`. Lab stage 7: all four on the binarised scan. | "Closing is dilation then erosion; it bridges the small breaks Canny leaves in the page border so the contour closes." |
| Boundary extraction | Lab stage 7: A − (A ⊖ B). | "Boundary is the object minus its erosion: a one-pixel outline." |
| Hit-or-miss, thinning, thickening, gray-scale morphology | Not built. | "Thinning would give handwriting skeletons for stroke analysis; that is future work." |

### Unit 5 — Edge detection (CO3)

| Topic | Where | Viva line |
|---|---|---|
| First-order gradient: Prewitt, Sobel | Lab stage 6: Sobel |Gx|, |Gy|, magnitude. Canny computes Sobel gradients internally (aperture 3). | "Sobel is Prewitt with a centre weight of 2, so it smooths a little while it differentiates." |
| Second-order: Laplacian, LoG | App: `Imgproc.Laplacian(gray, CV_64F)` variance = sharpness score in `measureQuality` (< 65 ⇒ "Soft image"). Lab stage 6: Laplacian and LoG. | "Blur removes high frequencies, so the variance of the Laplacian drops; below 65 we ask the user to hold steady." |
| Canny | App: `Canny(blurred, edges, 55.0, 170.0)` in `detectCorners`. Lab stage 6. | "Canny gives thin, connected edges via non-maximum suppression and hysteresis; 170 seeds strong border edges, 55 lets them continue through shadow." |

### Unit 6 — Image segmentation (CO4)

| Topic | Where | Viva line |
|---|---|---|
| Pixel relationships; 4-, 8-, m-connectivity; component labelling | Lab stage 8: connected components on the binarised scan with 4- and 8-connectivity (component counts differ). App: `findContours` (border following) on the closed edge map. | "With 4-connectivity a diagonal stroke breaks into pieces; 8-connectivity keeps a character whole; m-connectivity removes 8-connectivity's double paths." |
| Thresholding, Otsu | Lab stage 8: global slider vs Otsu vs adaptive. Benchmark: `fig_threshold_compare.png`. | "Otsu picks the one threshold that maximises between-class variance; it is optimal only when the histogram is bimodal." |
| Adaptive (local) thresholding | App: `adaptiveThreshold(..., ADAPTIVE_THRESH_GAUSSIAN_C, THRESH_BINARY, 35, 11.0)` in B&W mode. | "Each pixel is compared with a Gaussian-weighted mean of its 35×35 neighbourhood minus 11." |
| Region-based segmentation | App (applied): the page is segmented as the largest convex 4-vertex contour covering ≥15 % of the frame (`findDocumentCorners`, `approxPolyDP` ε = 2 % of perimeter). Region growing / split-and-merge, Bayesian and EM binarisation: not built. | "Our page segmentation is contour-based: find closed boundaries, keep the biggest convex quadrilateral." |

### Unit 7 — Colour image processing (CO1, CO2)

| Topic | Where | Viva line |
|---|---|---|
| Colour models RGB, HSV, YUV (+ CMY/CMYK, HSI theory) | Lab stage 9: RGB, HSV and YUV channel views of the scan. App: RGBA → gray. | "Y in YUV is exactly the luminance we feed OCR; HSV separates the colour of a stamp from how brightly it is lit." |
| Model transformation | App: `COLOR_RGBA2GRAY`, `COLOR_GRAY2RGBA`; PP-OCR input is gray replicated into 3 channels (NCHW). Lab stage 9. | "Grayscale is a projection of RGB onto one axis; we go back to 3 channels only because the recogniser expects them." |
| Colour slicing | Lab stage 9: hue-range slice isolates the red stamp (synthetic certificate) from black ink. | "A hue band around red picks out the stamp whatever its brightness; red wraps around hue 0, so we take two bands." |
| Colour histogram processing | Lab stage 3/9: equalise V (or Y) only, keep hue. | "Equalise intensity, not R, G and B separately, or the stamp changes colour." |

### Unit 8 — Digital video and compression (CO5)

| Topic | Where | Viva line |
|---|---|---|
| Temporal correlation, I/P/B frames | Lab stage 11 explanation panel; paper discussion. The camera preview is a video stream; the app captures one still (`ActivityResultContracts.TakePicture`). | "Consecutive preview frames are almost identical; that redundancy is what P and B frames exploit and what a steady-capture trigger can measure." |
| Motion estimation: exhaustive block matching, three-step search, 2-D log search | Lab stage 11 "steady capture": block matching between two frames, ES vs TSS vs 2-D log, search points and SAD cost. Benchmark: `fig_block_matching_cost.png`. | "For a ±7 window, exhaustive search tests 225 positions per block; three-step search tests 25." |
| Video compression link | Lab stage 11 + Unit 3 DCT: motion-compensated residual + DCT is the core of MPEG/H.26x. | "Motion vectors remove temporal redundancy; the DCT removes spatial redundancy in what is left." |

### Course outcomes

| CO | Bloom level | Strongest evidence | Viva line |
|---|---|---|---|
| CO1 — fundamentals, resolution, distance, formats | L2 | Three resolutions (1600 / 3200 / 48-px lines), Euclidean warp sizing, JPEG q88 storage, Lab stage 1 | "Resolution, distance and format choices are written into the code as named constants." |
| CO2 — point, spatial, histogram enhancement | L3 | `enhanceForOcr`: CLAHE → 3×3 median → unsharp or adaptive threshold; Lab stages 2–4; `fig_cer_stages.png` | "We applied enhancement and then measured its effect on OCR error, not just on how the page looks." |
| CO3 — morphology and edge analysis | L4 | `detectCorners`: Gaussian → Canny 55/170 → 5×5 close; Laplacian sharpness gate; Lab stages 6–7 | "We analysed why Canny plus closing beats Sobel alone for a closed page border." |
| CO4 — compare segmentation, Otsu | L4 | Lab stage 8; `fig_threshold_compare.png`; contour-based page segmentation | "We compared global, Otsu and adaptive thresholds on the same shadowed page and kept adaptive." |
| CO5 — video representation, motion estimation | L2 | Lab stage 11; `fig_block_matching_cost.png` | "We implemented three block-matching searches and counted their cost on real frames." |

---

## 2. Viva prep — 20 questions

**Q1. Why CLAHE and not global histogram equalisation?**
Global HE builds one cumulative mapping from the whole-page histogram, so the large white paper area dominates and a shadowed corner stays dark. It also stretches flat regions, amplifying paper grain and sensor noise. CLAHE computes a mapping per tile (8×8 grid), clips each tile histogram at 2.2 times the uniform bin height and redistributes the excess, which caps the mapping slope and therefore the noise gain. Tiles are blended bilinearly so no seams appear.

**Q2. Why does Otsu fail under uneven lighting?**
Otsu chooses one threshold that maximises between-class variance, which is optimal when the histogram has two well-separated modes. A shadow shifts the background gray level, so the paper becomes two or more modes and ink in the bright region overlaps paper in the dark region. Any single threshold then either blackens the shadow or erases faint ink. Adaptive thresholding computes T(x, y) locally, so it follows the illumination.

**Q3. Why Canny thresholds 55 and 170?**
They are hand-tuned on the 1600-px analysis image, not learned, and the ratio of about 1:3 sits in Canny's recommended 1:2 to 1:3 range. The high threshold 170 starts edges only on strong contrast such as the page-to-table border. Hysteresis then follows those edges down to 55, so the border survives where a shadow weakens it, while isolated weak texture never seeds an edge. The paper lists them as calibration candidates.

**Q4. What does the Laplacian variance measure?**
The Laplacian is a second-derivative operator (OpenCV default 3×3 kernel 0 1 0 / 1 −4 1 / 0 1 0), so it responds strongly to rapid intensity changes such as sharp text edges. Blur is a low-pass filter, so it removes those changes and the variance of the Laplacian response falls. We compute it on the 1600-px analysis image; below 65 the app shows "Soft image — hold steady and move slightly closer". It depends on content and resolution, so it is a heuristic gate, not a calibrated focus metric.

**Q5. How do four corners give you a homography?**
A planar homography is a 3×3 matrix with 9 entries defined up to scale, so 8 unknowns. Each point correspondence (x, y) → (u, v) gives two linear equations, so four corners, no three collinear, give exactly 8 equations. `getPerspectiveTransform` solves that system and `warpPerspective` resamples the page with bicubic interpolation. The output size is the longer of each pair of opposite edges (Euclidean lengths).

**Q6. Median or Gaussian for salt-and-pepper noise?**
Median. Impulse pixels are extreme outliers; a rank-order filter never outputs them as long as fewer than half the window is corrupted. A Gaussian is a weighted average, so it spreads each impulse into a gray blob and also blurs edges. We use a 3×3 median after CLAHE because CLAHE can lift speckle in flat paper.

**Q7. What is energy compaction in the DCT?**
For smooth, correlated image blocks the DCT puts most of the signal energy into a few low-frequency coefficients in the top-left of the 8×8 block. Quantisation can then zero most high-frequency coefficients with little visible loss, which is why JPEG works. Text is the hard case: sharp strokes have real high-frequency energy, so aggressive quantisation causes ringing and blocking around characters. The Lab and `fig_dct_quality_cer.png` show PSNR and OCR error as quality drops.

**Q8. Explain I, P and B frames.**
An I-frame is intra-coded, like a JPEG, and decodes on its own. A P-frame is predicted from a previous reference frame using motion vectors plus a DCT-coded residual. A B-frame is predicted bidirectionally from past and future references and is usually the smallest. A group of pictures repeats I, then P and B frames, so errors cannot propagate forever.

**Q9. Compare the complexity of exhaustive search and three-step search.**
For a search range of ±p, exhaustive search evaluates (2p + 1)² candidate positions per block: 225 for p = 7, each costing N² absolute differences for an N×N block. Three-step search evaluates 9 points, then 8 at half the step, then 8 again: 25 positions for p = 7, about 9× fewer. It can miss the global minimum because it assumes the error surface decreases monotonically towards it. 2-D log search moves a "+" pattern and halves the step only when the centre wins, so its count depends on the motion; the Lab counts it on the real frames.

**Q10. Why convert to grayscale before OCR?**
Text legibility is carried by luminance contrast, not hue, so the Y channel holds almost all the useful information. One channel is a third of the data, and CLAHE, median and thresholding are defined cleanly on a single channel. Coloured stamps and backgrounds become gray levels that the contrast stages can suppress. The one place colour helps, isolating a stamp, is shown in the Lab with HSV slicing.

**Q11. Why a morphological closing on the Canny edges?**
Canny edges are one pixel thick and break wherever contrast dips: at a shadow, a fold or a finger. `findContours` then sees an open curve and no quadrilateral. A 5×5 closing (dilate then erode) bridges gaps up to roughly the kernel size and then thins the result back, so the border becomes a closed loop without growing the page.

**Q12. Which colour model would you use to detect stamps?**
HSV (or HSI). Hue describes the ink colour independent of brightness, so a red or blue stamp can be selected with a hue band plus a minimum saturation, even under uneven light. In RGB the same stamp spans a long, lighting-dependent range on all three axes. In OpenCV red wraps around hue 0/180, so red stamps need two hue bands.

**Q13. Why analyse at 1600 px but run OCR at up to 3200 px?**
Detecting a page border and estimating brightness and blur need only coarse structure, and fewer pixels make Canny and contour search faster and less sensitive to paper texture. OCR needs enough pixels per character stroke, so the warp runs on the higher-resolution image using corners scaled back up. The app downsamples only; it never upsamples a small photo.

**Q14. Write the unsharp-masking equation you use.**
Output = a·I + (1 − a)·G_σ(I) = I + (a − 1)·(I − G_σ(I)). I − G_σ(I) is the high-pass "mask". Auto mode uses σ = 1.05, a = 1.32 (adds 32 % of the mask); Clean mode uses σ = 1.35, a = 1.50. Compared with Laplacian sharpening it is less sensitive to single-pixel noise because σ controls which frequencies are boosted.

**Q15. Why Canny and not Sobel or LoG for the page border?**
Sobel gives gradient magnitude with thick, unthresholded responses and no continuity. LoG finds zero crossings and produces many closed "spaghetti" contours on texture. Canny adds Gaussian smoothing, non-maximum suppression (one-pixel edges) and hysteresis (connected edges), which is what contour finding needs. The Lab shows all four on the same scan.

**Q16. What is the difference between 4-, 8- and m-connectivity, and where does it matter?**
4-connectivity counts only horizontal and vertical neighbours; 8 adds diagonals; m-connectivity uses diagonals only when the shared 4-neighbours are not foreground, which removes the multiple-path ambiguity of 8-connectivity. In the Lab, 4-connectivity splits diagonal strokes so one character becomes several components. For character or stamp counting, 8-connectivity matches what the eye sees.

**Q17. Why is the B&W mode not the default?**
Binarisation throws away anti-aliased gray edges that recognisers use, and a wrong local threshold deletes faint strokes permanently. Auto mode keeps gray levels and only sharpens, so ML Kit receives a natural-looking image. B&W (adaptive, block 35, C 11) is offered for faded or high-contrast printouts and is measured separately in the benchmark.

**Q18. Why add an 18-pixel white border?**
After a tight warp, characters can touch the image edge and text detectors often miss boxes that touch the boundary. A constant white border gives every line a margin, and white matches the page background so it adds no edge.

**Q19. How do you know the image processing helps?**
The paper's first draft said honestly that no paired measurement existed. The IVP benchmark (`ml/ivp_eval/results.json`, `fig_cer_stages.png`) runs OCR on the same synthetic certificates as raw photo, rectified and enhanced and reports character error rate for each. These are synthetic images with known ground truth, so they isolate the effect of each stage; they are not a field-accuracy claim on real certificates.

**Q20. Which syllabus topics are not in the app, and why?**
Hadamard transform, hit-or-miss, thinning and thickening, gray-scale morphology, region growing and split-and-merge, and Bayesian / EM binarisation are not built. The page is a single large region with a strong border, so contour detection is cheaper and more reliable than region growing, and adaptive thresholding already handles illumination. Frequency-domain filtering and motion estimation are demonstrated in the Pipeline Lab rather than in the capture path, because small spatial kernels are cheaper than an FFT round-trip and the app captures a single still.

---

## 3. IVP deck plan — 15 slides, 600 seconds

**Look (from the shared brief).** 1920×1080. Paper #F7F4EE, mist #E9E3D6, ink #0D1B2A, muted #5B6472, blue #1F4BD8, yellow #FFC72C, red #D62828 (sparingly). Bricolage Grotesque 800 headlines 72–100 px; Instrument Sans body ≥24 px (usually 32); DM Mono uppercase kickers ≈30 px with letter-spacing. Footer pinned bottom 64 px, 24 px DM Mono: `AADIDEV RAIZADA · N152 · IMAGE & VIDEO PROCESSING · NMIMS MPSTME`, slide number right. 128 px side margins. One idea per slide.

**x-embed rules.** Each animated visual is a self-contained mini page ≤16 KB of inline HTML/CSS/JS, system font stack (`system-ui, -apple-system, "Segoe UI", Roboto, sans-serif`; mono `ui-monospace, Consolas, monospace`), no network, no images, transparent or #F7F4EE background, loops forever, respects `prefers-reduced-motion` by showing the final frame.

**Arc.** Hook (2) → problem (3) → big idea (4) → pipeline (5) → syllabus route (6) → four IVP stage groups (7–10) → video unit (11) → measured evidence (12) → live demo / screens (13) → limitations + future (14) → callback close + Q&A (15).

**★ form sequence (no form twice in a row).** 1 equation · 2 number-led · 3 paradox · 4 strikethrough · 5 domain pun · 6 number-led · 7 equation · 8 metaphor · 9 paradox · 10 bold claim · 11 domain pun · 12 metaphor · 13 quote-as-title · 14 parallel two-beat · 15 callback.

**Timing.** 20 + 35 + 35 + 30 + 45 + 40 + 50 + 45 + 45 + 50 + 45 + 50 + 50 + 35 + 25 = **600 s**.

### 3.0 Asset status at time of writing

See the end of this file ("Asset check") for which of the Section 0 files existed when this plan was finalised. Slides reference files by the names in Section 0; a slide whose file is missing runs on its x-embed.

---

### Slide 1 — `title` · 20 s

- **Kicker:** `IMAGE & VIDEO PROCESSING · 2026-27`
- **Headline (fixed):** NavCert Offline
- **Subtitle ★ (equation):** Crooked photo + classical IVP = a certificate you can search
  - Alt (bold claim): No signal needed to read a certificate
  - Alt (metaphor): A scanner made of a phone and eight syllabus units
- **Body (exact):** Aadidev Raizada · N152 · B.Tech · NMIMS MPSTME Mumbai · Image & Video Processing · 2026-27
- **Visual:** right half: `lab/sample_certificate_photo.jpg` (synthetic, skewed and shadowed) with a thin blue arrow to `app_preview_aligned.png`. If the screenshot is missing, use the x-embed of slide 7 frozen on its last frame.
- **Build:** headline → subtitle → name line → image.
- **Speaker notes (20 s):**
  - "Good morning. I'm Aadidev Raizada, N152. This is NavCert Offline."
  - "It turns a phone photo of a seafarer's certificate into clean, searchable text and five fields — with no network at all."
  - "In the next ten minutes I'll show you that almost every unit of this course is doing real work inside it."
  - Cue: smile, one breath, click.

### Slide 2 — `hook-four-defects` · 35 s

- **Kicker:** `THE PHOTO`
- **★ (number-led):** 4 defects. 1 photo. 0 bars of signal.
  - Alt (question): What does a ship's cabin do to a photograph?
  - Alt (metaphor): Every phone photo is a small storm
- **Body (exact, 4 callout labels on the image):** Perspective skew · Uneven light and shadow · Blur and sensor noise · A red stamp over the text
- **Visual:** full-height `app/src/main/assets/lab/sample_certificate_photo.jpg` (synthetic; generated by `tools/make_sample_certificate.py`). Four numbered red (#D62828) circles with leader lines to the defects.
- **Build:** photo → callouts 1, 2, 3, 4 one per click (or auto 0.6 s apart).
- **Speaker notes (35 s):**
  - "This is a synthetic certificate — every name on it is invented — photographed the way a seafarer actually does it."
  - Click through: "One: the page is a trapezium, not a rectangle. Two: the light falls off across the page and there's a shadow. Three: a little hand-shake blur and sensor noise. Four: a red stamp sitting on top of the text."
  - "And on a ship, or in a port office, there's often no signal to send this to a cloud OCR service — and these are private documents anyway."
  - Back-pocket: "Why synthetic? The real corpus is a client's private certificates; I never put them in slides or the paper."

### Slide 3 — `problem` · 35 s

- **Kicker:** `THE PROBLEM`
- **★ (paradox):** The OCR is ready. The photo isn't.
  - Alt (strikethrough): OCR doesn't fail at ~~reading~~ seeing
  - Alt (bold claim): No recogniser was trained on your kitchen table
- **Body (exact, two columns):**
  - Left "What OCR expects": Upright page · Even light · High contrast · Sharp strokes
  - Right "What a phone gives": Projective distortion · Illumination gradient · Noise and blur · JPEG blocking
- **Visual:** two-column table, left in ink, right in red. Thin arrow between rows.
- **Build:** left column → right column row by row.
- **Speaker notes (35 s):**
  - "On-device recognisers like ML Kit and PP-OCR are good. But they were built for upright, evenly lit text."
  - "The phone hands them the opposite: a projected page, a light gradient, noise, blur, and JPEG artefacts on top."
  - "So the question for this course is simple: how much of that gap can classical image processing close before any neural network sees a pixel?"
  - Cue: pause after "before any neural network sees a pixel".

### Slide 4 — `big-idea` · 30 s

- **Kicker:** `THE IDEA`
- **★ (strikethrough reveal):** Fix the ~~model~~ pixels first
  - Alt (equation): Geometry + light + noise = the three things to fix
  - Alt (parallel two-beat): Training data: none needed. Cost: milliseconds of arithmetic.
- **Body (exact, 3 chips):** Deterministic · Explainable at every stage · No training data
- **Visual:** the word "model" struck through in red; "pixels" appears in blue after 0.8 s. Three chips below on mist background.
- **Build:** headline with "model" → strike → "pixels" → chips.
- **Speaker notes (30 s):**
  - "The tempting fix is a bigger model. I went the other way: fix the pixels first."
  - "Classical IVP is deterministic, I can show you the output of every stage, and it needs no training data — which matters, because my labelled data is small."
  - "The neural models then do what they're good at: reading clean text."
  - Back-pocket: "There are learned detectors, like Differentiable Binarization, but a bounded paper page has a strong border, so contours are enough and cheaper."

### Slide 5 — `pipeline` · 45 s

- **Kicker:** `THE PIPELINE`
- **★ (domain pun):** A dry dock for documents
  - Alt (metaphor): An assembly line where every station is a syllabus unit
  - Alt (number-led): 11 stages. 0 network calls.
- **Body (exact, horizontal flow, two rows):**
  - Row 1 "Find the page" (1600 px): Gray · Gaussian 5×5 · Canny 55/170 · Close 5×5 · Contours → convex quad ≥15 % · Quality gate (mean <55 / >220, Laplacian var <65)
  - Row 2 "Clean the page" (≤3200 px): Perspective warp · CLAHE 2.2, 8×8 · Median 3×3 · Unsharp (Auto/Clean) or adaptive threshold 35/11 (B&W) · 18 px white border → ML Kit / PP-OCRv5 → 5 fields
- **Visual:** `docs/figures/fig_pipeline.png` if present; otherwise the flow drawn as rounded-rectangle chips (ink outline, mist fill), row 1 arrows blue, row 2 arrows yellow.
- **Build:** row 1 → row 2 → OCR/fields tail.
- **Speaker notes (45 s):**
  - "Ships go into dry dock to be straightened and cleaned before they sail again. Documents get the same treatment here."
  - "Row one finds the page on a 1600-pixel copy: grayscale, Gaussian blur, Canny, a morphological close, then the biggest convex quadrilateral. In parallel, brightness and Laplacian variance decide whether to warn the user."
  - "Row two cleans the page at up to 3200 pixels: perspective warp, CLAHE, a median filter, then sharpening — or adaptive thresholding in black-and-white mode — and a white border."
  - "Only then does OCR run, all on the phone. Every number on this slide is a constant in DocumentScanner.kt."
  - Back-pocket: "Why two resolutions? Border detection needs structure, OCR needs stroke detail."

### Slide 6 — `syllabus-route` · 40 s

- **Kicker:** `SYLLABUS MAP`
- **★ (number-led):** 8 units. 1 scan.
  - Alt (domain pun): Every port of call on the syllabus
  - Alt (bold claim): No unit of this course sits idle in this app
- **Body (exact, chip labels):**
  - Stations along a route: Capture → Quality → Edges → Page → Warp → Light → Noise & sharpen → Binarise → Colour → Store → Steady capture
  - Unit chips under stations: U1 Fundamentals (Capture, Warp, Store) · U5 Edges (Quality: Laplacian; Edges: Canny) · U4 Morphology (Edges: close) · U6 Segmentation (Page: contours; Binarise: Otsu/adaptive, components) · U2 Enhancement (Light: CLAHE; Noise & sharpen) · U3 Transforms (Store: JPEG DCT; Lab: DFT) · U7 Colour (Colour: HSV stamp slice) · U8 Video (Steady capture: block matching)
  - Legend (24 px mono): ● App path ○ Pipeline Lab
- **Visual — x-embed `route-map` (≤16 KB):**
  - A horizontal route line (ink, 4 px) with 11 station dots, slight S-curve across two rows to fit 1920 px.
  - A yellow (#FFC72C) "ship" dot travels the route in 9 s (ease-in-out per leg, 0.8 s each), pauses 1.5 s at the end, loops.
  - When the ship reaches a station, that station's unit chips pop from mist (#E9E3D6) to blue (#1F4BD8) fill with white text (scale 0.9 → 1.0, 200 ms). Chips for App-path stations are solid; Lab-only stations (Colour, Steady capture, DFT) use a dashed blue outline.
  - Bottom counter in mono: "Units lit: n / 8", reaching 8/8 at the last station.
  - Implementation: one SVG path + `getPointAtLength` in `requestAnimationFrame`; chips are absolutely positioned divs; ≈6 KB.
  - Fallback still: the same chip map, static, with all chips lit.
- **Build:** route draws on (0.6 s) → ship starts → final hold shows all chips lit.
- **Speaker notes (40 s):**
  - "Here is the same pipeline as a route through the syllabus."
  - "Unit 1 at capture and warp — resolution and Euclidean distance. Unit 5 at the quality gate and edges. Unit 4 closes the border. Unit 6 segments the page and the text. Unit 2 fixes the light and noise. Unit 3 every time a preview is saved as JPEG."
  - "The dashed stops — colour slicing, the Fourier spectrum, and motion estimation — run in the Pipeline Lab on the user's own scan, because they explain the pipeline rather than sit in the capture path. I'll be precise about which is which."
  - Cue: point at the counter when it hits 8/8.

### Slide 7 — `find-the-page` · 50 s (Units 1, 4, 5, 6)

- **Kicker:** `EDGES · MORPHOLOGY · GEOMETRY`
- **★ (equation):** Canny + closing + 4 corners = a flat page
  - Alt (metaphor): Drawing a fence around the page
  - Alt (number-led): 8 unknowns. 4 corners. Solved.
- **Body (exact, 4 numbered lines):**
  1. Gaussian 5×5 → Canny, hysteresis 55 / 170
  2. Close 5×5: dilate, then erode — bridges broken border edges
  3. Contours → approxPolyDP (ε = 2 % perimeter) → convex quad ≥ 15 % of frame
  4. Homography from 4 corners (8 equations) → bicubic warp, size = longest opposite edges
- **Visual — x-embed `corner-warp` (≤16 KB), 7 s loop:**
  - 0.0–1.0 s: a skewed page (SVG polygon, white with 10 gray text-line bars) on a mist "table" with a soft shadow.
  - 1.0–2.0 s: edge view: page fades to 20 %, its outline drawn as a 2 px ink stroke with 3 small gaps (dash array) — label "Canny".
  - 2.0–2.8 s: gaps close (dash array animates to solid), stroke thickens briefly then thins — label "Close 5×5".
  - 2.8–3.6 s: four red (#D62828) corner dots pop in order TL, TR, BR, BL with labels "1 2 3 4" — label "4 corners".
  - 3.6–5.2 s: polygon points interpolate to an upright rectangle (JS lerp of the 4 points, ease-in-out); text bars follow via the same bilinear map — label "warpPerspective".
  - 5.2–7.0 s: hold, blue tick and "Page aligned" in mono (the app's actual status string).
  - Right side: `docs/screenshots/lab_10_geometry.png` (Lab contour → quad → warp on the synthetic photo) at 560 px wide; `lab_06_edges.png` and `lab_07_morphology.png` as two small thumbnails under it.
- **Build:** headline → x-embed starts → numbered lines 1–4 appear in sync with the animation labels → screenshots.
- **Speaker notes (50 s):**
  - "Step one, find the page. A 5×5 Gaussian removes paper grain, then Canny. The high threshold, 170, only starts edges on strong contrast like the page border; hysteresis lets them continue down to 55 through the shadow."
  - "Canny edges still break. A 5×5 closing — dilation then erosion — bridges those breaks, so the contour is closed."
  - "Contours are simplified to polygons; I keep the largest convex four-sided one covering at least 15 % of the frame."
  - "Four corners give eight equations, exactly the eight unknowns of a homography. warpPerspective does the rest."
  - Back-pocket: "If no quad is found, the app keeps the whole image and the user can crop manually in the crop editor."
  - Back-pocket: "Corner ordering uses x+y and y−x; it can confuse corners on a page rotated near 45°, which is in my limitations."

### Slide 8 — `fix-the-light` · 45 s (Unit 2: point + histogram; Unit 1 quantization)

- **Kicker:** `HISTOGRAM PROCESSING`
- **★ (metaphor):** 64 dimmer switches, not one
  - Alt (strikethrough): Equalise the ~~page~~ tile
  - Alt (paradox): Global equalisation brightens the page and still loses the shadow
- **Body (exact):**
  - Global HE: one mapping for the whole page → shadow stays dark, flat paper gets noisy
  - CLAHE: 8×8 tiles, histogram clipped at 2.2×, bilinear blend → even page, capped noise
  - Point operations in the Lab: negative · contrast stretch · log · gamma · slicing
- **Visual — x-embed `clahe-tiles` (≤16 KB), 8 s loop:**
  - Left: a 360×480 canvas "page" generated in JS: white paper with a dark gradient shadow in the lower-right third and rows of short dark bars (text).
  - Right: its 64-bin histogram (ink bars) with a cumulative curve (blue line).
  - 0–2 s: "Original" label.
  - 2–4 s: "Global HE": the page remaps by the global CDF; the histogram spreads; shadow region remains dark; a small red note "shadow lost".
  - 4–5 s: an 8×8 grid fades in over the page (1 px blue lines).
  - 5–7 s: "CLAHE 2.2 · 8×8": each tile remaps by its own clipped CDF (precomputed per tile in JS); a yellow horizontal clip line appears on a single highlighted tile's mini histogram; the page evens out.
  - 7–8 s: hold.
  - Fallback / companion: `docs/screenshots/lab_03_histogram.png` (and `lab_02_point.png`, `lab_01_resolution.png` as small thumbnails).
- **Build:** headline → x-embed → Global HE line → CLAHE line → Lab line + thumbnails.
- **Speaker notes (45 s):**
  - "Next, the light. Global histogram equalisation uses one dimmer switch for the whole page. The white paper dominates the histogram, so the shadow stays dark and flat paper turns grainy."
  - "CLAHE uses 64 dimmer switches — an 8 by 8 grid of tiles, each with its own mapping. Each tile's histogram is clipped at 2.2 times the average bin, which caps how much noise it can amplify. Tiles are blended bilinearly so there are no seams."
  - "In the Lab, the same scan also goes through the textbook point operations: negative, contrast stretch, log, gamma and slicing, plus resolution and gray-level quantization."
  - Back-pocket (Q1, Q2 in Section 2).

### Slide 9 — `noise-sharpen-transforms` · 45 s (Unit 2 neighbourhood, Unit 3)

- **Kicker:** `SPATIAL · FREQUENCY · DCT`
- **★ (paradox):** Blur it to sharpen it
  - Alt (equation): I + k·(I − Gσ·I) = sharper text
  - Alt (domain pun): Calm seas for the median, high tide for the edges
- **Body (exact):**
  - Median 3×3: removes speckle CLAHE lifts, keeps stroke edges
  - Unsharp mask: out = I + (a − 1)(I − G_σ I) · Auto σ 1.05, a 1.32 · Clean σ 1.35, a 1.50
  - DFT spectrum + low-pass: what smoothing removes
  - 8×8 DCT: every saved preview is JPEG q88
- **Visual:** left `docs/screenshots/lab_04_spatial_frequency.png`; right `docs/screenshots/lab_05_dct.png`. Under them, if present, `docs/figures/fig_dct_quality_cer.png` at 700 px wide.
  - Optional x-embed `dct-zigzag` (≤16 KB, 6 s loop) if `lab_05_dct.png` is missing: an 8×8 grid of squares whose blue opacity = |coefficient| of a text-edge block (hard-coded 64 values computed from a synthetic 8×8 block in JS with a direct DCT-II); a yellow zig-zag path traces coefficients in order; squares beyond index k turn mist (zeroed) as k steps 64 → 6; beside it the reconstructed 8×8 block (inverse DCT in JS) and a mono PSNR readout computed live.
- **Build:** headline → median line → unsharp equation → spectrum image → DCT image.
- **Speaker notes (45 s):**
  - "Here's a paradox: to sharpen, we blur. Subtract a Gaussian-blurred copy from the image and what's left is the edge detail. Add a fraction of it back — 32 % in Auto, 50 % in Clean."
  - "Before that, a 3×3 median removes the speckle that CLAHE lifted. A median ignores outliers; a Gaussian would smear them."
  - "The Lab shows the same story in frequency: the DFT spectrum of the page, and a low-pass filter that removes exactly the high frequencies the text lives in."
  - "And Unit 3 is in production too: every stored preview is JPEG at quality 88 — 8×8 DCT blocks. The Lab compresses the scan block by block and reports PSNR."
  - Back-pocket: "Why not filter in the frequency domain in the app? For a 3×3 or 5×5 kernel, direct convolution is cheaper than an FFT round trip; the convolution theorem says the result is the same."
  - Back-pocket (Q7 DCT energy compaction).

### Slide 10 — `segment-and-colour` · 50 s (Units 4, 6, 7)

- **Kicker:** `SEGMENTATION · COLOUR`
- **★ (bold claim):** No single threshold survives a shadow
  - Alt (equation): T(x, y) = local mean − 11
  - Alt (metaphor): Otsu draws one waterline; shadows are tides
- **Body (exact):**
  - Global: one T — shadow turns black or faint ink vanishes
  - Otsu: best single T for a two-peak histogram — shadows break the two peaks
  - Adaptive (B&W mode): Gaussian mean of 35×35 − 11, per pixel
  - Components: 4- vs 8-connectivity · Morphology: open, close, boundary
  - Colour: HSV hue band lifts the red stamp off black ink
- **Visual — x-embed `threshold-sweep` (≤16 KB), 9 s loop:**
  - A 480×340 canvas: procedurally drawn page (text bars) multiplied by a horizontal illumination ramp (100 % left → 45 % right) plus mild noise; rendered as gray.
  - Panel A "Global T" (0–3 s): T sweeps 40 → 220; the binarised view updates each frame; a mono readout "T = 128"; at no T is the whole page clean (left side loses ink when right side is right).
  - Panel B "Otsu" (3–5 s): T jumps to the Otsu value computed in JS from the canvas histogram (between-class variance maximisation); a yellow marker drops onto a mini histogram; the right side is black.
  - Panel C "Adaptive 35 / 11" (5–8 s): integral-image local mean (box approximation is fine for the animation), result clean edge to edge; blue tick.
  - 8–9 s hold.
  - Companions: `docs/screenshots/lab_08_threshold.png` (large), `lab_07_morphology.png` and `lab_09_colour.png` as thumbnails; `docs/figures/fig_threshold_compare.png` if present.
- **Build:** headline → x-embed → three threshold lines in sync → components/morphology line → colour line + thumbnail.
- **Speaker notes (50 s):**
  - "Black-and-white mode needs a threshold. A global one fails: there is no value that keeps faint ink in the bright corner and clean paper in the shadow."
  - "Otsu is the textbook answer — the threshold that maximises between-class variance. It's optimal when the histogram has two clean peaks. A shadow splits the paper into two peaks, and Otsu gives up the dark side."
  - "So the app compares every pixel with the Gaussian mean of its own 35 by 35 neighbourhood, minus 11. That's adaptive thresholding."
  - "On the binary page, the Lab labels connected components — 4-connectivity breaks diagonal strokes, 8 keeps characters whole — and runs opening, closing and boundary extraction."
  - "Colour: in HSV, hue separates the red stamp from black ink whatever the lighting. That's colour slicing, Unit 7."
  - Back-pocket (Q2, Q12, Q16).

### Slide 11 — `steady-capture` · 45 s (Unit 8)

- **Kicker:** `VIDEO · MOTION ESTIMATION`
- **★ (domain pun):** Dead reckoning for pixels
  - Alt (number-led): 225 checks or 25? Same motion vector.
  - Alt (question): What if the camera told you when your hand was still?
- **Body (exact):**
  - Preview = video: consecutive frames are nearly identical (temporal correlation)
  - Block matching, ±7 window, SAD cost
  - Exhaustive: (2p + 1)² = 225 positions · Three-step: 9 + 8 + 8 = 25 · 2-D log: "+" pattern, step halves when centre wins
  - I · P · B: intra, forward-predicted, bidirectional
- **Visual — x-embed `block-search` (≤16 KB), 10 s loop:**
  - A 15×15 grid of small squares (the ±7 search window) on mist; the true motion vector cell is outlined in ink.
  - Phase 1 "Exhaustive" (0–3 s): all 225 cells fill gray in raster order (≈13 ms each); counter "ES: 225 SAD".
  - Phase 2 "Three-step" (3–6.5 s): step 4: 9 yellow dots (centre + 8 at distance 4); best one turns blue; step 2: 8 yellow around it; step 1: 8 more; final blue cell lands on the true vector; counter "TSS: 25 SAD".
  - Phase 3 "2-D log" (6.5–9 s): blue "+" patterns of 5 points step 4 → 2 → 1 (step halves when the centre is best), final 3×3 check; counter shows the cumulative count produced by the JS run.
  - 9–10 s: bar comparison of the three counts.
  - Companions: `docs/screenshots/lab_11_motion.png` and `docs/figures/fig_block_matching_cost.png` (search points / time measured on real frame pairs).
- **Build:** headline → first body line → x-embed phase by phase with matching lines → I/P/B line.
- **Speaker notes (45 s):**
  - "Navigators used dead reckoning: estimate where you are from how far you've moved. Video codecs do the same with pixels."
  - "The camera preview is a video. Consecutive frames are nearly identical, so for each block in the new frame we search the old one for the best match — minimum sum of absolute differences."
  - "Exhaustive search over plus-or-minus 7 pixels tests 225 positions per block. Three-step search tests 9, then 8, then 8 — 25 — about nine times fewer. 2-D log search walks a plus-shaped pattern."
  - "In the Lab this is a steady-capture demo: if the motion vectors are near zero, the hand is still and the frame is safe to capture. In the app today, capture is a single still photo, so this is the Lab, not the main path."
  - "Those motion vectors plus a DCT-coded residual are exactly what P- and B-frames carry."
  - Back-pocket (Q8, Q9). "TSS can miss the true minimum on non-monotonic error surfaces; ES never does."

### Slide 12 — `evidence` · 50 s

- **Kicker:** `MEASURED EVIDENCE`
- **★ (metaphor):** One page, three photos, one ruler
  - Alt (number-led): 3 versions. 1 ground truth. 0 guesses.
  - Alt (strikethrough): Improved, ~~we think~~ measured
- **Body (exact structure, values from `ml/ivp_eval/results.json` only):**
  - Stat tile 1: CER raw photo
  - Stat tile 2: CER rectified
  - Stat tile 3: CER rectified + enhanced
  - Line: Otsu vs adaptive under uneven light (from the same file)
  - Footnote (24 px muted): Synthetic certificates with known ground truth · ML Kit on-device OCR · isolates image-processing effects, not a field-accuracy claim
- **Visual:** `docs/figures/fig_cer_stages.png` (main, 1100 px wide) with three stat tiles above; `fig_threshold_compare.png` as a smaller second chart if space allows.
- **Build:** tiles one by one (raw → rectified → enhanced) → chart → footnote.
- **Speaker notes (50 s):**
  - "My first paper draft said honestly that I had no paired measurement. So I built one."
  - "Same synthetic certificates, known ground truth, same OCR engine. Three versions: the raw photo, after rectification, and after rectification plus enhancement. The ruler is character error rate."
  - Read the three CER values aloud from the tiles, then name the stage with the largest CER drop in `results.json` ("The biggest single step is rectification" or "... is enhancement", whichever the file shows). Quote the values as written; do not round in a direction that flatters.
  - "And the threshold comparison confirms the previous slide: under uneven light, adaptive beats Otsu."
  - "These are synthetic images, so this measures what image processing contributes. It is not an accuracy claim on real certificates — that needs a labelled field set, which is my next step."
  - Back-pocket: AI pilot numbers if asked about fields: fine-tuned MiniLM 15/26 (57.7 %) on a six-document human-checked gold set vs 4/26 for the tiny model; text-only, not end-to-end.

### Slide 13 — `live-demo` · 50 s

- **Kicker:** `ON THE PHONE`
- **★ (quote-as-title):** "Page aligned."
  - Attribution line under it (24 px mono): — NavCert Offline status line, after `DocumentScanner.prepare`
  - Alt (equation): Photo + 1 tap = 5 fields
  - Alt (bold claim): Nothing on this screen touched the internet
- **Body (exact, 3 captions under 3 phone frames):** Capture · Aligned and enhanced · Text + 5 fields
- **Visual:** three phone frames left → right: `docs/screenshots/app_home.png`, `app_preview_aligned.png`, `app_result_fields.png`. If live demo is possible (emulator or phone mirrored), run it instead and keep this slide as the backup.
- **Build:** frame 1 → frame 2 → frame 3.
- **Speaker notes (50 s):**
  - Live path (if mirroring works, 40 s): "Airplane mode is on." Open the app → gallery → `sample_certificate_photo` → preview shows "Page aligned" → toggle Auto / Clean / B&W to show the three modes → Extract → fields. Then open Pipeline Lab on the same scan and swipe two stages (histogram, threshold).
  - Backup path (screenshots): "This is the real app. The status line literally says 'Page aligned' — that's the warp from slide 7. Auto, Clean and B&W are the three enhancement modes from slides 8 to 10. Then OCR and the five fields: certificate name, issue date, port of issue, issuing authority, expiry date."
  - "All of it ran in airplane mode."
  - Cue: never wait more than 5 s on a live step; switch to screenshots.
  - Back-pocket: "If the border isn't found, the app keeps the whole image and offers a manual crop — it never invents a page."

### Slide 14 — `limits-and-next` · 35 s

- **Kicker:** `LIMITATIONS · FUTURE WORK`
- **★ (parallel two-beat):** Works on a phone. Not yet proven on a fleet.
  - Alt (bold claim): A heuristic is a hypothesis with a default value
  - Alt (strikethrough): Thresholds ~~tuned~~ guessed — next, calibrated
- **Body (exact, two columns):**
  - Limits: Canny 55/170 and blur gate 65 are hand-tuned · Quality is measured on the whole frame, not the page · Contours fail on borderless pages · Corner ordering weak near 45° · CER measured on synthetic pages only
  - Next: Calibrate thresholds on labelled phone photos · Steady-capture auto-trigger from block matching · Learned page detector when contours fail · Labelled real-certificate set: CER, field F1, latency
- **Visual:** two columns, red bullets left, blue bullets right; no image.
- **Build:** left column → right column.
- **Speaker notes (35 s):**
  - "Honest limits. The Canny thresholds and the blur threshold are heuristics I tuned by hand. Brightness and blur are measured on the whole frame, including the table. Contours need a visible page border. And my CER numbers are on synthetic pages."
  - "Next: calibrate those thresholds on labelled phone photos, turn the block-matching demo into an automatic steady-capture trigger, and measure on a labelled real-certificate set."
  - Back-pocket: "Why not a deep detector now? It costs size and latency, and contours already work when there is a border; I'd add it as a fallback, not a replacement."

### Slide 15 — `close-qna` · 25 s

- **Kicker:** `THANK YOU · QUESTIONS`
- **★ (callback):** 4 defects in. 1 clean page out. Still 0 bars.
  - Alt (metaphor): Out of dry dock, ready to sail
  - Alt (quote-as-title): "Image looks good." — NavCert Offline quality check
- **Body (exact):** Aadidev Raizada · N152 · aadidevraizada26@gmail.com · github.com/AadidevRaizada/navcert-offline
- **Visual:** left half: the four numbered defect circles from slide 2, now with blue ticks; right half: `app_preview_aligned.png`. If missing, freeze the last frame of the `corner-warp` x-embed.
- **Build:** headline → ticks animate onto 1–4 (150 ms apart) → contact line.
- **Speaker notes (25 s):**
  - "We started with four defects in one photo and no signal. Edges and morphology fixed the geometry, histogram processing fixed the light, filters fixed the noise, segmentation and colour separated ink from stamp — and it still runs with zero bars."
  - "Thank you. I'm happy to take questions."
  - Cue: stop talking, step back from the screen. For questions, use Section 2.

---

## 4. Code vs paper / brief contradictions (for the paper writer)

1. **Upscaling.** Paper III-B: "The rectified image is enlarged only when necessary." Code never enlarges; `DocumentScanner.scaleToMaximum` only downsizes (cap 3200 px for OCR, 1600 px for analysis). Fix the sentence.
2. **Enhancement modes.** Paper III-B: "The color and grayscale modes use unsharp masking." The code has three modes, `AUTO` (σ 1.05, amount 1.32), `CLEAN` (σ 1.35, amount 1.50) and `BLACK_WHITE` (adaptive threshold 35 / 11). All three output grayscale (`enhanceForOcr` converts to gray first). There is no colour mode.
3. **Where quality is measured.** Paper III-A implies the checks describe the document. `measureQuality` runs on the whole 1600-px analysis frame before rectification, so the background counts toward brightness and Laplacian variance. Checks are ordered dark → bright → blur and only the first failure is shown.
4. **Extractor chain is out of date.** Paper (abstract, III-E, IV-D) says rules → MobileBERT fallback and treats tiny-BERT as the experimental model. The app now runs rules → fine-tuned MiniLM int8 ONNX (`assets/nlp/certificate_minilm.int8.onnx`, 32.3 MiB) → MobileBERT. The MiniLM results in `ml/README.md` (35/49 weak-val, 15/26 gold vs 4/26 tiny) are missing from the paper.
5. **Correction and field extraction are skipped in handwriting mode.** Paper III-D/E present them as general stages. In `MainActivity.extractText`, the handwriting branch returns early: `OcrTextCorrector` and certificate-field extraction run only in the printed/auto path.
6. **Handwriting input image.** The paper should say PP-OCRv5 line crops come from the enhanced grayscale (or binarised, in B&W mode) bitmap, with margins of 4 % horizontally and 22 % vertically, width padded to a multiple of 8, and pixels normalised to [−1, 1].
7. **Missing detection details** (not contradictions, but examiners ask): Gaussian is 5×5 with σ derived by OpenCV (≈1.1); `approxPolyDP` ε = 2 % of perimeter; the 20 largest contours are tested; the largest convex quad wins; warp uses bicubic interpolation with replicated borders; output size = longest opposite edges (aspect ratio is approximate).
8. **"3 pixel median"** → "3×3 median".
9. **No evaluation of image processing.** Paper VI-A says no paired raw-versus-enhanced set exists. Once `ml/ivp_eval/results.json` exists, replace that sentence with the synthetic benchmark (raw vs rectified vs enhanced CER, Otsu vs adaptive, DCT quality vs CER) and label it synthetic.
10. **Video unit.** The capture path uses `ActivityResultContracts.TakePicture` (single still). Motion estimation exists only in the Pipeline Lab demo. The paper must not describe a live steady-capture trigger in the main app.
11. **Byline.** Paper byline is "Project N152 Research Team"; it must be Aadidev Raizada, NMIMS MPSTME Mumbai.
12. **PRODUCT.md audience.** It describes "students and everyday users scanning printed documents or handwritten notes". The brief and paper position the app for maritime / seafarer certificates. Align one of them.
13. **Leftover file.** `GroceryItem.kt` (unused, contains `picsum.photos` image URLs) is unrelated to the app and contains network URLs in a "fully offline" codebase. Recommend deleting it before submission.
14. **Corner ordering limitation.** `orderCorners` uses min/max of x + y and y − x; on a page rotated near 45° two corners can collide. Worth one line in Limitations.

---

## Asset check (2026-10-12, when this plan was committed)

| Expected | Status |
|---|---|
| `docs/screenshots/*.png` (14 files in Section 0) | Not yet present. Every slide that uses them has an x-embed or a static fallback. |
| `docs/figures/*.png` (5 files in Section 0) | Not yet present. Slide 5 falls back to the chip flow; slides 9–11 fall back to their x-embeds; slide 12 needs `fig_cer_stages.png` or the three stat tiles alone. |
| `ml/ivp_eval/results.json` | Not yet present (`pipeline.py`, `synth.py`, `ocr_metrics.py` exist). Slide 12 quotes values only from this file. |
| `docs/research/` | Not yet present. Viva answers above rely on textbook definitions and the code, not on external citations. |
| Pipeline Lab code | `IvpOperations.kt` + `IvpOperationsTest.kt` present; `DocumentScanner` stage functions made `internal` for reuse. |
| Synthetic sample | `app/src/main/assets/lab/sample_certificate_photo.jpg`, `sample_certificate_flat.png` (from `tools/make_sample_certificate.py`) present; used on slides 1, 2, 13. |

Deck builder: before building, re-list the three folders and map any differently named files onto the Section 0 roles (same content, any file name).
