# Topic notes for NavCert Offline

Short explainers in plain language. Each note says what the idea is, then where NavCert Offline uses it. Parameters quoted for the pipeline are the ones in the app code. Reference numbers point to `references.md`.

# Part 1. Image and Video Processing (IVP)

## Unit 1. Image fundamentals
- A digital image is a 2-D function f(x, y) sampled on a grid; stored as a matrix whose entries are intensities (grey) or three channels (colour) [37, 38].
- Spatial resolution is the number of pixels; grey-level resolution is the number of intensity levels (256 for 8 bits). Text legibility depends on both.
- Distance measures matter for neighbourhoods: Euclidean distance for geometry, city-block (D4) and chessboard (D8) for pixel connectivity, and m-adjacency to avoid double paths.
- File formats trade size for fidelity: PNG is lossless, JPEG is DCT-based and lossy, TIFF is a flexible archival container [53].
- In the app: a photo is first limited to a maximum analysis dimension of 1600 px so every later step runs on a predictable matrix, and OCR input is capped at 3200 px. ML Kit asks for characters of at least 16x16 pixels, which is why resolution is never reduced blindly [35].

## Unit 2a. Point processing
- A point operation maps each pixel independently: s = T(r). Examples are the digital negative, contrast stretching (piecewise linear), thresholding, intensity slicing, log and power-law (gamma) transforms [37].
- Contrast stretching maps the occupied intensity range onto the full range; thresholding is the extreme case with two output levels.
- In the app: the Pipeline Lab shows negative, contrast-stretch, gamma and intensity-slice transforms, and the image-quality panel reports mean brightness, classing below 55 as dark and above 220 as bright.
- Thresholding is also the last step of the enhancement path (see Unit 6).

## Unit 2b. Histogram equalization and CLAHE
- The histogram counts pixels per intensity. Equalisation maps intensities through the cumulative distribution so that the output histogram is closer to flat, spreading crowded grey levels [37].
- Global equalisation can wash out regions and amplify noise. Adaptive histogram equalisation works per tile [44]. CLAHE clips each tile's histogram at a limit before equalising and interpolates between tiles, so noise is not blown up [45].
- In the app: CLAHE with clip limit 2.2 on 8x8 tiles lifts faint stamps and pale print on scanned or shadowed pages.

## Unit 2c. Spatial filtering (smoothing, sharpening, median, Gaussian)
- Neighbourhood filters convolve the image with a small kernel. Smoothing filters average (box) or weight by a Gaussian; sharpening filters add a Laplacian-type high-pass response [37, 38].
- The median filter replaces a pixel with the neighbourhood median. It removes salt-and-pepper noise and keeps edges better than an average.
- Unsharp masking subtracts a blurred copy from the original and adds the difference back with a gain.
- In the app: Gaussian blur precedes edge detection for page finding; a 3x3 median removes sensor speckle; unsharp masking (sigma 1.05 to 1.35, amount 1.32 to 1.50) restores stroke contrast after rectification.

## Unit 2d. Frequency-domain filtering
- The Fourier transform expresses an image as sinusoids. Low frequencies carry smooth illumination; high frequencies carry edges and noise [37].
- A low-pass filter blurs, a high-pass filter sharpens, and convolution in space equals multiplication in frequency.
- A Gaussian in space is a Gaussian in frequency, which is why Gaussian blur has no ringing.
- In the app: the Pipeline Lab computes a centred Fourier magnitude spectrum of the page; the bright horizontal and vertical cross in it comes from lines of text. The Gaussian and unsharp filters are applied in the spatial domain with small kernels, which is cheaper than transforming the whole image.

## Unit 3. Transforms and the DCT
- The Discrete Cosine Transform represents an 8x8 block as a sum of cosine patterns and packs most energy into a few low-frequency coefficients [52].
- JPEG applies the DCT per block, divides by a quantisation table (the lossy step), and entropy-codes the result [53].
- High quantisation leaves blocking and ringing around sharp text edges.
- In the app: photos from the camera or gallery arrive JPEG-compressed, so enhancement runs after decoding and blocking noise near thin strokes is part of what the filters must tolerate.

## Unit 4. Morphological processing
- Morphology probes a binary or grey image with a structuring element. Erosion shrinks foreground, dilation grows it, opening (erode then dilate) removes specks, closing (dilate then erode) fills gaps [54, 55].
- Boundary extraction is the image minus its erosion; thinning and thickening change stroke width; grey-scale morphology uses min and max.
- In the app: after Canny, a 5x5 morphological close joins broken edge segments so the page outline becomes one connected contour that contour following can trace [46].

## Unit 5. Edge detection
- Edges are intensity discontinuities. First-order operators (Sobel, Prewitt) estimate the gradient magnitude [41, 42]; second-order operators (Laplacian) find zero crossings, and the Laplacian of Gaussian smooths first [43].
- Canny combines Gaussian smoothing, gradient computation, non-maximum suppression and hysteresis thresholding with two thresholds to give thin, connected edges [40].
- In the app: Canny with thresholds 55 and 170 on the blurred grey image finds the page border. Contours are followed [46], simplified with Douglas-Peucker [47], and a quadrilateral whose area is at least 15% of the frame is accepted as the page.
- The variance of the Laplacian response is used as a sharpness score: below 65 the image is classed as blurry [49].

## Unit 6. Segmentation
- Thresholding separates foreground from background. Otsu's method chooses the global threshold that maximises between-class variance, which is equivalent to minimising within-class variance [39].
- Local methods compute a threshold per neighbourhood so uneven lighting does not break the result: Niblack uses mean plus k times standard deviation [51]; Sauvola adapts it for documents [50].
- Region-based methods (region growing, split and merge) and connected-component labelling with 4-, 8- or m-connectivity group pixels by similarity or adjacency [37].
- In the app: adaptive thresholding with block size 35 and constant 11 is used for the binarised variant, because a shadow across a photographed page defeats one global Otsu threshold. The Pipeline Lab also counts 8-connected components of the binarised ink and compares Otsu with the adaptive result.

## Unit 7. Colour image processing
- Colour models describe the same colour in different axes: RGB (display), CMY and CMYK (print), HSI/HSV (hue, saturation, intensity, which separates colour from brightness), YUV (luma plus chroma, used in video and JPEG) [37, 38].
- Processing the luminance channel alone avoids colour shifts; histogram processing of colour images is therefore done on intensity, not on each RGB channel independently.
- In the app: equalisation and thresholding act on grey luminance, because OCR needs contrast, not colour. The Pipeline Lab also uses HSV range masks (blue hues about 100 to 135 and red hues near 0 and 180) to pick out blue and red ink or stamps, which is easier in HSV than in RGB.

## Unit 8. Video: frames and block-matching motion estimation
- Video has strong temporal correlation: consecutive frames are nearly identical. Codecs code I frames independently, P frames by prediction from earlier frames and B frames by prediction from both directions [37].
- Block matching splits the current frame into blocks and searches the previous frame for the best-matching block, usually by sum of absolute differences. The displacement is the motion vector.
- Exhaustive (full) search tests every offset in the window and is optimal but costly. Three-step search starts with a coarse step and halves it each round [57]. 2-D logarithmic search shrinks the step when the best match stays at the centre [56].
- In the app: the Pipeline Lab implements exhaustive search, three-step search and 2-D logarithmic search on 16x16 blocks with a search range of +/-7 pixels, and reports points tested per block, mean SAD and time for each. The dominant exhaustive-search vector between two preview frames measures camera shake: a magnitude above 1.5 px gives "Camera moving: wait for a steady frame". Mobile document benchmarks such as MIDV-500 and SmartDoc are video-based for the same reason: capture is a stream of frames [25, 23].

## Perspective correction (applied across units)
- A flat page seen from an angle maps to the image by a 3x3 homography; four corner correspondences determine it [48].
- The four detected corners are mapped to an upright rectangle with a perspective warp. Learning-based dewarpers such as DocUNet and DocTr handle curved pages but are much heavier [21, 22].

# Part 2. Artificial Intelligence (AI)

## Agent and environment (PEAS)
- A rational agent chooses the action expected to maximise its performance measure given what it has perceived [80].
- Performance: correct extraction of the five fields (certificate name, issue date, port of issue, issuing authority, expiry date), safe abstention when unsure, speed, no data leaving the phone.
- Environment: a photographed certificate under variable light and angle; partially observable (text hidden by stamps), stochastic (OCR noise), single agent, sequential.
- Actuators: on-screen results, flags on uncertain fields, retake prompts, local storage. Sensors: the camera and the user's questions.

## Search
- A problem-solving agent searches a state space for a path to a goal; uninformed methods (BFS, DFS, uniform cost) use no domain knowledge, informed methods (greedy, A*) add a heuristic [80].
- In the app: finding the page outline is a search over candidate contours, filtered by area and shape; field extraction tries strategies in a fixed order (explicit label rules, then the fine-tuned MiniLM model, then MobileBERT if ONNX is unavailable), so cheaper and more reliable alternatives are tried before costlier ones.
- Extractive QA is also a search: the model scores start and end positions and the best valid span is returned [27].

## Knowledge representation and rules
- Knowledge can be held as production rules (IF condition THEN action). Forward chaining starts from facts and fires rules to derive new facts; backward chaining starts from a goal and looks for supporting facts [80, 82].
- In the app: `CertificateFieldExtractor` is a small production-rule system over the OCR text. For issue date it tries the pattern "Issued at <place> on <date>" first, then labelled lines such as "date of issue" or "issue date", and only then asks the QA model, and only if issue wording is present in the text. Expiry, authority and port follow the same pattern with their own label phrases. Rules are tried in priority order and the first match fills the field.
- Edit distance [81] is used in `OcrTextCorrector`: a word of four or more letters that is not in the dictionary, has no digits and is not all capitals is compared with dictionary words after mapping common confusions; a replacement is accepted only if it is within distance 1 (distance 2 for words of seven or more letters) and clearly better than the runner-up.

## Constraint satisfaction (field consistency)
- A CSP has variables, domains and constraints; solving means assigning values that violate none [80].
- Framing for the report: the variables are the five fields; the domains are the candidate strings or dates found in the text; the constraints are that a date is a valid calendar date, the expiry date is later than the issue date, and a field with no supporting text stays empty.
- What the app does today: evidence constraints. A QA model is consulted for a field only when the text contains the matching wording (for example expiry wording for the expiry date), and answers must be 2 to 160 characters and, for dates, contain a date pattern.
- A cross-field check that the expiry date is later than the issue date is a natural next constraint; it is not implemented in the extractor, so present it as the CSP formulation and future work, not as shipped behaviour. Backtracking or constraint propagation would then discard the inconsistent candidate.

## Supervised learning and fine-tuning
- Supervised learning fits a model to labelled examples. Fine-tuning starts from a pre-trained model and continues training on a small domain dataset [27].
- The base here is MiniLM, distilled from a larger transformer [29] and already trained for extractive question answering on SQuAD 2.0, which includes unanswerable questions [30, 31]. It was fine-tuned on certificate text with weak labels and evaluated on a six-document, human-checked gold set (15 of 26 exact answers, against 4 of 26 for the earlier tiny model; see `ml/README.md`).
- Quantising to int8 cut the export to 32.3 MiB. Integer quantisation trades a small accuracy change for size and speed [32].

## Expert-system architecture
- An expert system has a knowledge base (facts and rules), an inference engine, working memory, an explanation facility and a user interface; development moves from knowledge acquisition to rule coding to testing [82, 80].
- Mapping to the app: the rules are the knowledge base; the engine applies them over OCR text in working memory; the interface presents the extracted fields to the user; limitations are the usual ones, brittle rules and costly maintenance, which is why a learned model backs the rules up.

# Part 3. Mobile Application Development (MAD)

## Jetpack Compose and Material 3
- Compose is the declarative UI toolkit for Android: the UI is a function of state, and when state changes the affected composables are redrawn [69].
- Material 3 supplies theming through `MaterialTheme` (colour scheme, typography, shapes) and ready components such as cards, buttons and navigation [70, 71].
- Rule for the report: use Material components and theme tokens throughout so the app follows one design language.

## Activities and intents
- An Activity is a screen entry point; an Intent is a message that requests an action from another component, used to start an activity, start a service or deliver a broadcast [78].
- Typical uses here: launching the camera, opening a document picker for import, and sharing or opening a saved page.

## Camera capture and import
- Two routes exist. A camera Intent (`MediaStore.ACTION_IMAGE_CAPTURE`) hands capture to the default camera app, so no camera library is needed [75]. CameraX is the library route, with preview, image capture and image analysis use cases for API 21 and later [76].
- NavCert uses the first route through the `ActivityResultContracts.TakePicture` contract, imports from the gallery through `GetContent`, and opens a separate `CropEditorActivity` for manual crop. CameraX is the upgrade path if live in-preview checks are wanted.

## Permissions
- Ask for a permission in context, when the user starts the feature that needs it; explain why first; keep the app usable if it is denied [77].
- NavCert's manifest removes the `INTERNET` and `ACCESS_NETWORK_STATE` permissions that the ML Kit dependency would otherwise add, so image data and analytics cannot leave the device. This is the privacy argument for the Play Data safety form [73].

## File handling and local storage
- App-specific storage in internal memory is private to the app, needs no permission and is deleted on uninstall [74].
- `DocumentRepository` keeps documents, previews and folders inside `filesDir` (JSON files plus preview images). Room, an SQLite abstraction for structured local data [79], is the alternative if queries over many documents are needed; it is not used today.

## On-device ML runtimes
- ML Kit text recognition runs from a bundled model with no first-use download (about 4 MB per script per architecture), which makes print OCR fully offline [35].
- ONNX Runtime Mobile runs ONNX models on Android through the `onnxruntime-android` package, with CPU as the default and optional NNAPI or XNNPACK acceleration [33]. The app uses it for PP-OCRv5 handwriting recognition and the int8 MiniLM field model.
- LiteRT is built on TensorFlow Lite [34]; the app includes the TensorFlow Lite Task text library for the bundled MobileBERT question answering. OpenCV supplies the classical vision steps [36].

## Play readiness
- New apps and updates must target API level 36 from 31 August 2026 [72]; the project already sets `targetSdk = 36` with `minSdk = 24`. The Data safety form must be completed even when no data is collected [73].
