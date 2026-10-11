# NavCert Offline: MAD rubric scorecard, architecture and deck plan

Course: Mobile Application Development (702AI0E002), NMIMS MPSTME Mumbai, 2026-27
Student: Aadidev Raizada (N152), solo project
Repository: `github.com/AadidevRaizada/navcert-offline`, branch `main`
Status of this document: written against the code at commit `39d6fbe` (12 Oct 2026). The Pipeline Lab screen is being added in parallel; sections that depend on it say so.

---

## 0. One-paragraph summary

NavCert Offline is a Kotlin + Jetpack Compose (Material 3) Android app that turns a phone photo of a maritime certificate into an editable, searchable document without any network access. OpenCV finds and flattens the page, enhances it, ML Kit reads printed text, a bundled PP-OCRv5 model (ONNX Runtime) reads handwriting, a rules-first extractor backed by a fine-tuned int8 MiniLM (ONNX) and MobileBERT (TensorFlow Lite) fills five certificate fields, documents are saved in app-private storage, and a retrieval + MobileBERT assistant answers questions about saved documents. The `INTERNET` and `ACCESS_NETWORK_STATE` permissions are stripped in the manifest, so nothing can leave the phone.

Predicted total today: **30 / 40**. With the five cheapest fixes in section 1.6: **34–35 / 40**.

---

## 1. Rubric scorecard

### 1.1 Criterion 1: Originality of problem & user needs + expressing technical details (5 marks)

| | |
|---|---|
| Evidence | Domain-specific problem (maritime certificates, offline at sea and in port), not a generic scanner. Field schema chosen for the domain: certificate name, issue date, port of issue, issuing authority, expiry date (`CertificateFieldExtractor.kt`). Extractor rules encode real certificate language: "issued at … on …", "valid until", "document of compliance", "register of shipping", and reject battery/equipment dates (`equipmentDate` regex). Real corpus behind the design: 108 PDFs / 351 pages of vessel and seafarer certificates (`ml/README.md`). Tools match the constraints: on-device OCR and QA because there is no reliable connectivity, manifest-level network removal because identity documents are sensitive. |
| Predicted band | **4 (Good)**. Originality is strong (5-level). User-needs research is the weak half: `PRODUCT.md` still describes the users as "students and everyday users", and there is no written persona, interview or sourced statistic in the repo yet. |
| Gaps → smallest fix | (a) Rewrite the Users section of `PRODUCT.md` around the maritime personas in section 4 (30 min). (b) Add a one-page user-needs table (need → evidence → feature) using `docs/research/facts.md` sources (1 h). (c) If any genuine conversations with seafarers, manning agents or a ship manager happened, record date, role and one paraphrased pain point each; never fabricate. (d) Rename the app from "N152" to "NavCert Offline" so the product story is visible on the device (5 min, also helps criterion 3). |

### 1.2 Criterion 2: Self-learning / new logic or component added (5 marks)

| | |
|---|---|
| Evidence | 12+ concepts outside the syllabus, each wired into the working app (section 2). Syllabus units cover controls, intents, ListView, WebView, camera intent, pickers, broadcast receivers, Maps, SQLite/Firebase. None covers on-device ML, native image processing, ONNX/TFLite, coroutines or Compose adaptive layouts. |
| Predicted band | **5 (Excellent)**. The rubric needs three; the project has well over three, each relevant to the problem. |
| Gaps → smallest fix | Presentation only: show each concept next to the screen that uses it and the file that implements it, so the examiner does not have to take it on trust (slide S09). The Pipeline Lab screen makes the OpenCV concept visible in the demo. |

### 1.3 Criterion 3: Standardized workable application (Play Store + Material Design) (10 marks)

| | |
|---|---|
| Evidence (for) | Compose Material 3 throughout: `Scaffold`, `TopAppBar`, `ModalNavigationDrawer` / `PermanentNavigationDrawer` switch at 840 dp (adaptive layout for tablets), `NavigationDrawerItem` with badge, `FilterChip` mode selectors, `OutlinedTextField`, `AlertDialog`, `LazyColumn`, Material icons; custom `lightColorScheme` and `Typography` in `ui/theme/`. `targetSdk = 36`, `compileSdk = 36`, `minSdk = 24`. No runtime permissions needed: camera through `ActivityResultContracts.TakePicture` + `FileProvider` (cache path only), gallery through `GetContent`. Network permissions removed via `tools:node="remove"`. Content descriptions on icon-only buttons (move, delete, highlight, bookmark, close). Error and progress states shown in the UI (`status` text, `isProcessing`). Back handling with `BackHandler`. |
| Evidence (against) | App label is `N152` (`strings.xml`) and the launcher icon is the Android Studio template icon. Release build has `isMinifyEnabled = false`, no `shrinkResources`, no signing config, `versionName = "1.0"` default. Debug APK is **449.8 MiB**; measured composition of the 23 Sep debug APK (`app/build/outputs/apk/debug/app-debug.apk`, 472.5 MiB on disk) is: native libraries for four ABIs ≈ 310 MiB uncompressed (OpenCV 136.0, ONNX Runtime 101.3, ML Kit OCR 39.1, TFLite Task Text 29.2 MiB summed over ABIs), `assets/nlp` 128.3 MiB (MobileBERT 95.8 MiB + MiniLM int8 32.3 MiB), dex 60.0 MiB uncompressed, PP-OCRv5 7.6 MiB. Lab-exercise leftovers ship in the app: `GroceryItem.kt` (with `picsum.photos` URLs) and `ResultActivity.kt` (harmonic-series lab, declared in the manifest). `allowBackup="true"` with template backup rules, so saved certificate text and previews could go to cloud backup. Light theme only: no dark scheme, no dynamic colour, no `enableEdgeToEdge()` in `MainActivity` (Android 15+ enforces edge-to-edge at target 35+). No privacy policy, no Data safety answers, no store listing assets. State in `remember` (bitmaps, field results) is lost on rotation because there is no `ViewModel`. |
| Predicted band | **6 (Average, top)**. The app is workable and Material 3 is followed for most screens, but it is not yet "as per Play Store requirements". |
| Gaps → smallest fix | See the ranked list in 1.6. The four that move this criterion to 8: rename + icon, delete lab leftovers, release build with R8 + ABI-split App Bundle, privacy policy + Data safety draft + backup exclusion. To reach 9: ship the two QA models through Play Asset Delivery (install-time or on-demand asset pack) so the base module is small, add dark theme + edge-to-edge, and upload to an internal testing track as proof. |

### 1.4 Criterion 4: Individual contribution with evidence (10 marks)

| | |
|---|---|
| Evidence | Solo project: every layer is the student's own work: Android app (14 Kotlin source files, ≈ 4,000 lines incl. tests), OpenCV pipeline, model integration, Python data and training pipeline (`ml/prepare_certificates.py`, `bootstrap_ocr.py`, `finalize_recognition_labels.py`, `prepare_certificate_qa.py`, `prepare_augmented_qa.py`, `train_certificate_qa.py`, `evaluate_certificate_qa.py`, `export_certificate_qa.py`), synthetic-data tool (`tools/make_sample_certificate.py`). Iteration evidence in `ml/README.md`: tiny-BERT prototype 35.3% val / 23.5% test EM → MiniLM fine-tune 71.4% weak-val, 57.7% (15/26) human-checked gold vs 15.4% (4/26) → int8 export 32.3 MiB keeping 15/26 gold. Dated artefacts: build folder created 3 Aug, PP-OCRv5 model 10 Aug, MobileBERT 2 Sep, MiniLM int8 export 23 Sep, Pipeline Lab sample assets 12 Oct. |
| Predicted band | **7 (Good)**. Contribution is clearly defined, but the git history does not show the timeline: all 8 commits carry the timestamp 12 Oct 2026 00:46 because the work was committed as logical layers in one sitting. The "team spirit" wording does not fit a solo project and needs a deliberate answer. |
| Gaps → smallest fix | (a) Add `docs/contribution.md`: a dated timeline (Aug → Oct) built from the artefact dates above and the `ml/README.md` experiment log, plus a "problems I hit and how I solved them" table (APK size, handwriting fallback when ONNX fails, model asset materialisation, weak labels vs gold set, network-permission removal). 1 h. (b) Keep committing in small, honest steps from now on (Pipeline Lab, release build) so the final history shows real iteration. Never back-date commits. (c) For "team spirit": name who tested or reviewed (faculty feedback, peers who tried the APK) only if it really happened, and show how feedback changed the app. |

### 1.5 Criterion 5: Viva / presentation / demo (10 marks)

| | |
|---|---|
| Evidence | All modules are integrated in one flow: capture → crop → OpenCV preview → OCR → fields → edit/highlight → save → library/folders → ask. Previously installed and run on an OPPO CPH2665 (Android 15). Unit tests pass for the pure-Kotlin logic. Pipeline Lab with a bundled synthetic certificate removes the dependency on a camera and on private data during the demo. |
| Predicted band | **8 (Good)**, rising to 9 if the live demo runs cleanly end to end on a phone. Risk: no measured end-to-end accuracy or latency on the phone (`ml/README.md` says so explicitly), and the first ONNX/TFLite load is slow on a cold start. |
| Gaps → smallest fix | (a) Rehearse on the real phone with the synthetic certificate printed on paper; warm the models once before presenting. (b) Record a 60-second backup screen capture. (c) Prepare the 15 viva answers in section 6. (d) Measure and quote one honest number on the demo phone (median time from "Extract" to fields shown, five runs) if time allows; otherwise say it was not measured. |

### 1.6 Gaps ranked by marks gained per hour

| Rank | Fix | Effort | Criterion | Expected gain | Marks / hour |
|---|---|---|---|---|---|
| 1 | Set `app_name` to "NavCert Offline", replace template launcher icon with an adaptive icon (Image Asset Studio) | 0.3 h | C3, C1 | +0.5 | ≈ 1.7 |
| 2 | Delete `GroceryItem.kt` and `ResultActivity.kt` + its manifest entry | 0.2 h | C3 | +0.25 | ≈ 1.2 |
| 3 | Rewrite `PRODUCT.md` Users section + user-needs table from section 4 | 1 h | C1 | +1 | 1.0 |
| 4 | `docs/contribution.md` timeline and problem log | 1 h | C4 | +1 | 1.0 |
| 5 | Release config: `isMinifyEnabled = true`, `isShrinkResources = true`, keep rules for ONNX Runtime / TFLite / ML Kit / OpenCV JNI classes, signed `bundleRelease`; report per-device download size from bundletool | 1.5 h | C3 | +1 | 0.7 |
| 6 | Privacy policy page (no data collected, no network, local storage only) + Data safety form answers; set `allowBackup="false"` or exclude `files/documents` in `data_extraction_rules.xml` and `backup_rules.xml` | 1 h | C3 | +0.5 | 0.5 |
| 7 | Dark colour scheme + `enableEdgeToEdge()` + inset check on all screens | 2 h | C3 | +0.5 | 0.25 |
| 8 | Move screen state into a `ViewModel` (survives rotation; cleaner viva answer) | 3 h | C3, C5 | +0.5 | 0.17 |
| 9 | Play Asset Delivery pack for `mobilebert_qa.tflite` and `certificate_minilm.int8.onnx` | 4 h | C3 | +0.5 | 0.12 |
| 10 | Room database for document metadata (CO3 "database connectivity"); keep image previews as files | 5 h | CO3 alignment, C3 | +0.5 | 0.10 |

Notes on items 9 and 10:
- **APK size.** An App Bundle already removes three of the four ABIs from each device download; for arm64 the native libraries total ≈ 67 MiB in the measured APK. The two QA models (128.1 MiB together) then dominate. Play applies a download-size cap to the base module, so the models belong in an asset pack (confirm the current cap in Play Console Help before quoting it). A further option is to drop MobileBERT for field extraction and keep only the 32.3 MiB MiniLM, but that changes document Q&A and must be measured first.
- **Database.** The app stores each document as `files/documents/<uuid>.json` plus `<uuid>.jpg`, and folders in `files/folders.json`, written atomically through a temp-file rename. This is valid CO2 evidence (file handling, text and images). CO3 asks for database connectivity; if the examiner applies it to the mini project, a Room `@Entity Document` / `@Dao` with `Flow` queries is the cleanest upgrade, and it also enables full-text search (FTS4) in place of the in-memory index. Location tracking (CO3) is deliberately absent: the app has no network and never needs the user's position; say so in the viva rather than bolt on a map.

---

## 2. New concepts beyond the syllabus

| # | Concept | Where in the code | Why the problem needs it |
|---|---|---|---|
| 1 | On-device OCR with ML Kit Text Recognition v2 (bundled Latin model, block/line/element layout) | `MainActivity.extractText`, `DocumentTextFormatter.kt` | Reads printed certificates with no download and no server. |
| 2 | ONNX Runtime for Android running PP-OCRv5 mobile recognition, with own line cropping, NCHW tensor packing and CTC greedy decoding | `PaddleHandwritingRecognizer.kt` | Handwritten entries (endorsements, signatures blocks, manual dates) that ML Kit misreads. Falls back to ML Kit text if the model fails. |
| 3 | TensorFlow Lite Task Library `BertQuestionAnswerer` (MobileBERT) | `OfflineDocumentAssistant.kt` | Natural-language questions over saved documents, offline. |
| 4 | Fine-tuned, int8-quantized MiniLM extractive QA exported to ONNX, with a hand-written WordPiece tokenizer and 384-token sliding windows (stride 96) | `CertificateOnnxAnswerer.kt`, `ml/train_certificate_qa.py`, `ml/export_certificate_qa.py` | Fills certificate fields that rules miss; trained on the project's own certificate text (15/26 human-checked gold vs 4/26 for the earlier tiny model). |
| 5 | Hybrid extraction: deterministic rules first, ML only when evidence exists, abstain on non-certificates | `CertificateFieldExtractor.kt` | Avoids hallucinated dates; tested by `unrelatedDocumentDoesNotAskCertificateModel` and `invalidQaDateIsNotShownAsCertificateMetadata`. |
| 6 | OpenCV native image processing: Canny edges, morphological close, quadrilateral detection, perspective warp, CLAHE, adaptive threshold, unsharp mask, blur/brightness quality checks | `DocumentScanner.kt` | Phone photos of certificates are skewed, shadowed and uneven; OCR needs a flat, high-contrast page. |
| 7 | Retrieval over local documents: overlapping 1,200-character chunks (180 overlap) scored with TF-IDF-style weighting, title terms weighted 3×, top 4 chunks sent to the QA model | `DocumentKnowledgeIndex` in `OfflineDocumentAssistant.kt` | Lets a 384-token QA model answer across a whole library. |
| 8 | Kotlin coroutines with explicit dispatchers (`Default` for CV/ML, `IO` for files), `rememberCoroutineScope`, `LaunchedEffect`, `DisposableEffect` to close native sessions and recycle bitmaps | `MainActivity.kt` | Keeps the UI responsive during heavy native work and prevents native memory leaks. |
| 9 | Jetpack Compose + Material 3 with an adaptive navigation drawer (permanent ≥ 840 dp, modal below), `rememberSaveable` state, `VisualTransformation` text highlighting | `MainActivity.kt`, `ScanWorkflowScreens.kt`, `AppScreens.kt` | Modern declarative UI; works on phones and tablets used in ship offices. |
| 10 | Activity Result API: `TakePicture` with a `FileProvider` URI, `GetContent`, and `StartActivityForResult` to a crop editor; third-party `CropImageView` hosted in Compose via `AndroidView` | `MainActivity.kt`, `CropEditorActivity.kt`, `res/xml/file_paths.xml` | Camera capture without the CAMERA permission and without exposing file paths. |
| 11 | Coil image loading for library thumbnails | `AppScreens.kt` (`AsyncImage`) | Smooth scrolling over saved previews. |
| 12 | Manifest merger rules to strip library-requested permissions (`tools:node="remove"`) | `AndroidManifest.xml` | Guarantees no network path for identity documents, even from dependencies. |
| 13 | Large-model packaging: `noCompress` assets, materialising ONNX models to `filesDir` with a size check, lazy and synchronized session creation | `build.gradle.kts`, `CertificateOnnxAnswerer.getSession`, `PaddleHandwritingRecognizer.materializeAsset` | ONNX Runtime needs a file path; lazy loading keeps cold start fast. |
| 14 | Unit testing of pure Kotlin logic with JUnit 4 (12 tests in 6 classes) | `app/src/test/...` | Extraction, tokenizer, formatting, retrieval and OCR correction are verified without a device. |
| 15 | Python ML pipeline: PDF → page images, deduplication, PDF-disjoint splits, PP-OCR bootstrap labels with a review queue, QA fine-tuning, int8 export, gold-set evaluation | `ml/*.py`, `ml/README.md` | The fine-tuned model in concept 4 exists because of this pipeline. |
| 16 | Pipeline Lab (being added): a screen that shows each image-processing stage on a bundled synthetic certificate | `assets/lab/`, `tools/make_sample_certificate.py` | Makes the invisible pipeline demonstrable and explainable; demo works without a camera or private data. |

---

## 3. Architecture (slide-ready)

### 3.1 Components

| Layer | Component | File |
|---|---|---|
| Entry | `MainActivity` (launcher, `ComponentActivity`; loads OpenCV, hosts `OcrApp()` composable) | `MainActivity.kt` |
| Secondary activity | `CropEditorActivity` (explicit intent, returns cropped URI) | `CropEditorActivity.kt` |
| Navigation | `AppDestination` enum: Scan, Documents, Ask, About; navigation drawer (modal < 840 dp, permanent ≥ 840 dp). Scan sub-flow `ScanStep`: CAPTURE → PREVIEW → RESULT, with `BackHandler` stepping back | `MainActivity.kt`, `ScanWorkflowScreens.kt` |
| Screens | `CaptureScreen`, `DocumentPreviewScreen`, `ExtractedDocumentScreen` (editor, highlights, review notes, certificate summary), `DocumentsScreen` (search, folders, move, delete), `DocumentAssistantScreen`, `AboutScreen`; Pipeline Lab (in progress) | `ScanWorkflowScreens.kt`, `AppScreens.kt` |
| Vision | `DocumentScanner` (OpenCV detect → warp → enhance → quality) | `DocumentScanner.kt` |
| OCR | ML Kit `TextRecognizer`; `PaddleHandwritingRecognizer` (ONNX Runtime, PP-OCRv5); `DocumentTextFormatter`; `OcrTextCorrector` (Levenshtein dictionary correction) | as named |
| Understanding | `CertificateFieldExtractor` (rules) → `CertificateOnnxAnswerer` (MiniLM int8) → MobileBERT fallback; `DocumentKnowledgeIndex` + `OfflineDocumentAssistant` for Q&A | as named |
| Storage | `DocumentRepository`: JSON + JPEG per document in `filesDir/documents/`, `folders.json`; atomic temp-file rename | `DocumentRepository.kt` |

### 3.2 Data flow (diagram spec)

Left-to-right pipeline of eight boxes, ink outlines on paper background, blue arrows; the "OCR" box splits into two lanes.

```
[1 Capture]            camera (TakePicture → FileProvider cache URI) | gallery (GetContent)
     │  Uri
[2 Crop (optional)]    CropEditorActivity → cropped Uri
     │  Bitmap (decoded on Dispatchers.IO)
[3 Process]            DocumentScanner.prepare on Dispatchers.Default
                       scale ≤1600 px analysis / ≤3200 px OCR → quality (dark/bright/blur)
                       → Canny 55/170 + 5×5 close → largest quad ≥15% area → perspective warp
                       → enhance mode Auto | Clean | B&W (CLAHE, median, adaptive threshold, unsharp, 18 px border)
     │  OCR bitmap + preview bitmap (≤1200 px)
[4 OCR]                lane A  Printed/Auto: ML Kit → DocumentTextFormatter → OcrTextCorrector
                       lane B  Handwriting: ML Kit line boxes → PP-OCRv5 per line (ONNX Runtime) → CTC decode
     │  text
[5 Extract]            CertificateFieldExtractor: rules → MiniLM int8 ONNX → MobileBERT TFLite
     │  5 fields + editable text
[6 Review]             edit text, highlight phrases, notes, bookmark, title
     │  StoredDocument
[7 Store]              filesDir/documents/<uuid>.json + <uuid>.jpg ; filesDir/folders.json
     │  List<StoredDocument>
[8 Ask]                DocumentKnowledgeIndex (chunk 1200/overlap 180, TF-IDF top 4) → MobileBERT → answer + source document
```

Side annotations on the diagram: a dashed red box around the whole pipeline labelled "No INTERNET permission"; small grey tags under boxes 3–5 and 8 reading "Dispatchers.Default"; under 1, 2, 7 "Dispatchers.IO".

### 3.3 Threading

- UI state lives in Compose (`remember` / `rememberSaveable`) inside `OcrApp()`; all work is launched from `rememberCoroutineScope()`.
- `Dispatchers.IO`: image decoding (`ImageDecoder` on API 28+, `BitmapFactory` below), repository reads/writes, preview decode.
- `Dispatchers.Default`: rotation, OpenCV processing, preview scaling, PP-OCRv5 inference, field extraction, Q&A.
- ML Kit returns a Play Services `Task`; its success listener runs on the main thread and then launches a coroutine for the ML stages.
- ONNX sessions are created lazily under a lock; `OfflineDocumentAssistant` is a `lazy(SYNCHRONIZED)` so MobileBERT loads only when first needed.
- Native resources (ML Kit client, ONNX sessions, TFLite answerer, bitmaps) are released in `DisposableEffect.onDispose`.

### 3.4 Storage format

```json
// filesDir/documents/<uuid>.json
{ "id", "title", "text", "createdAt", "updatedAt", "wordCount", "lineCount",
  "characterCount", "previewPath", "reviewNotes", "highlightedPhrases": [],
  "bookmarked", "highlightColor", "folderId", "sourceType", "recognitionMode" }
// filesDir/documents/<uuid>.jpg   preview, JPEG quality 88
// filesDir/folders.json          [ { "id", "name", "createdAt" } ]
// filesDir/models/certificate/certificate_minilm.int8.onnx   materialised from assets
// cacheDir/n152_scan_*.jpg       camera captures (FileProvider "ocr_cache")
```

### 3.5 Permissions

| Permission | Status | Why |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Removed (`tools:node="remove"`) | ML Kit's transitive telemetry requests them; removing them guarantees offline operation. |
| `CAMERA` | Not requested | The system camera app captures through `ACTION_IMAGE_CAPTURE` into a `FileProvider` URI. |
| Storage / media | Not requested | Gallery import uses the system picker (`GetContent`), which grants one-time URI access. |
| Location | Not used | Not needed for the problem; privacy first. |

### 3.6 Dependencies (from `app/build.gradle.kts` and `gradle/libs.versions.toml`)

| Group | Artifact | Version |
|---|---|---|
| Build | Android Gradle Plugin | 9.0.1 |
| Build | Kotlin (Compose compiler plugin) | 2.0.21 |
| SDK | compileSdk / targetSdk / minSdk | 36 / 36 / 24 |
| AndroidX | core-ktx | 1.10.1 |
| AndroidX | lifecycle-runtime-ktx | 2.6.1 |
| AndroidX | activity-compose | 1.8.0 |
| Compose | compose-bom (ui, ui-graphics, tooling, material3, material-icons-extended) | 2024.09.00 |
| Images | io.coil-kt:coil-compose | 2.7.0 |
| Images | com.vanniktech:android-image-cropper | 4.7.0 |
| Vision | org.opencv:opencv | 4.13.0 |
| OCR | com.google.mlkit:text-recognition | 16.0.1 |
| ML runtime | com.microsoft.onnxruntime:onnxruntime-android | 1.24.1 |
| ML runtime | org.tensorflow:tensorflow-lite-task-text | 0.4.4 |
| Test | junit 4.13.2; androidx.test.ext:junit 1.1.5; espresso-core 3.5.1; compose ui-test-junit4 | |

Bundled models: `nlp/mobilebert_qa.tflite` (95.8 MiB), `nlp/certificate_minilm.int8.onnx` (32.3 MiB, CC BY 4.0 attribution in `MODEL_ATTRIBUTION.txt`), `nlp/vocab.txt`, `handwriting/en_PP-OCRv5_mobile_rec.ort` (7.6 MiB, Apache 2.0, SHA-256 in `NOTICE.txt`).

### 3.7 APK composition (measured on the 23 Sep debug APK, uncompressed MiB)

| Part | MiB | Note |
|---|---|---|
| Native libs, 4 ABIs | ≈ 310 | OpenCV 136.0, ONNX Runtime 101.3, ML Kit OCR 39.1, TFLite Task Text 29.2, libc++ 4.5 |
| of which arm64-v8a | ≈ 67 | what one modern phone actually needs |
| `assets/nlp` | 128.3 | MobileBERT + MiniLM int8 + vocab |
| dex (debug, unminified) | 60.0 | R8 will shrink this |
| `assets/handwriting` | 7.6 | PP-OCRv5 |
| ML Kit OCR models | 1.4 | |

Current debug build: 449.8 MiB. Slide message: "Most of the size is four copies of native code and two language models; an App Bundle plus an asset pack fixes both."

---

## 4. User needs framing

`docs/research/facts.md` did not exist when this file was written. Statements below avoid numbers; every statement that needs an external source is marked **[source]**. Replace the mark with a citation from `facts.md` when it lands, or drop the statement.

### 4.1 Who

| User | Relationship to certificates |
|---|---|
| Seafarers (officers and ratings) | Hold personal certificates of competency and proficiency, medical fitness and endorsements that must be valid to sign on **[source: STCW requirement to carry valid certificates]**. |
| Manning / crewing agents | Collect and verify crew certificates before deployment; receive them as phone photos and scans. |
| Ship managers and superintendents | Track vessel certificates (statutory, class, insurance, document of compliance) and their expiry across a fleet. The project corpus is largely this type. |
| Port State Control and inspection staff | Check certificates on board during inspections **[source: PSC inspection checks certificates and documents]**. |
| Maritime training institutes | Issue course certificates and answer verification requests. |

### 4.2 Pains

1. **Paper and photo chaos.** Certificates arrive as paper originals, scans and phone photos taken at an angle under poor light; text is not searchable.
2. **Expiry tracking.** Each certificate has its own issue and expiry date; missing a renewal can stop a seafarer joining a ship or delay a vessel **[source: consequences of expired certificates / PSC deficiencies]**.
3. **Connectivity.** Internet at sea is limited and costly, and port connectivity is unreliable; cloud OCR is not dependable where the documents are handled **[source: maritime connectivity cost/availability]**.
4. **Privacy.** Certificates carry identity data (name, date of birth, certificate numbers); uploading them to unknown cloud services is a data-protection risk **[source: India DPDP Act 2023 or GDPR as applicable]**.
5. **Manual re-typing.** Agents and managers copy the same five fields into spreadsheets or crew systems by hand.
6. **Fraud and verification burden** (secondary, not solved by this app) **[source: certificate fraud reports by IMO or flag states]**.

### 4.3 Needs → features

| Need | Feature | Evidence in app |
|---|---|---|
| Turn a skewed photo into a clean page | OpenCV detect + warp + enhance, crop editor, rotate, Auto/Clean/B&W modes | `DocumentScanner.kt`, `CropEditorActivity.kt` |
| Work with zero connectivity | All models bundled; network permissions removed | `AndroidManifest.xml`, `assets/` |
| Keep identity documents private | App-private storage, no account, no server | `DocumentRepository.kt`, About screen |
| Stop re-typing | Five certificate fields extracted and shown as a summary | `CertificateFieldExtractor.kt`, `CertificateSummary` |
| Read handwritten entries | Handwriting mode with PP-OCRv5 | `PaddleHandwritingRecognizer.kt` |
| Find a document fast | Library search, folders per vessel or crew member, bookmarks | `DocumentsScreen` |
| Answer "when does X expire?" | Offline Ask screen with source document shown | `DocumentAssistantScreen` |
| Trust the output | Editable text, review notes, highlights; extractor abstains on non-certificates | `ExtractedDocumentScreen`, tests |

Gap to state openly: expiry **reminders** (notifications) are not implemented yet; the expiry date is extracted but no alarm is scheduled. This is the most valuable next feature (WorkManager + local notification; no network needed).

---

## 5. MAD deck plan (≈ 10 minutes, 14 slides)

Look: 1920×1080, paper #F7F4EE, mist #E9E3D6 for alternate slides, ink #0D1B2A, muted #5B6472, blue #1F4BD8, yellow #FFC72C, red #D62828 sparingly. Headlines Bricolage Grotesque 800 (72–100 px), body Instrument Sans 32 px, kickers DM Mono uppercase ≈ 30 px tracked. Footer pinned bottom 64 px, 24 px DM Mono: `AADIDEV RAIZADA · N152 · MOBILE APPLICATION DEVELOPMENT · NMIMS MPSTME` + slide number right.

Screenshots expected in `docs/screenshots/`: `01_home.png` (Capture screen), `02_preview.png` (aligned preview with enhancement chips), `03_crop.png`, `04_result_text.png`, `05_certificate_fields.png`, `06_ask_document.png`, `07_library.png`, `08_about.png`, `10_lab_input.png`, `11_lab_edges.png`, `12_lab_corners.png`, `13_lab_warp.png`, `14_lab_enhance.png`, `15_lab_threshold.png`, `16_lab_ocr.png`. All taken with the synthetic certificate only.

Timing: 20 + 45 + 45 + 40 + 60 + 45 + 45 + 40 + 55 + 45 + 35 + 45 + 50 + 30 = **600 s**.

---

### S01 · Title · 20 s

- Kicker: `MAD MINI PROJECT · 2026-27`
- ★ **NavCert Offline** (name-as-title)
  - Alt 1 (promise): "Certificates, read on the phone, never sent anywhere"
  - Alt 2 (question): "What if the certificate desk fit in a pocket?"
- On-slide text: "NavCert Offline" / subline "Scan, read and question maritime certificates without the internet" / "Aadidev Raizada · N152 · NMIMS MPSTME Mumbai · Mobile Application Development · 2026-27"
- Visual: right half, phone frame with `01_home.png`; a small yellow "NO INTERNET PERMISSION" tag on the frame.
- Build: title → subline → phone slides up → byline.
- Speaker notes (20 s): "Good morning. I'm Aadidev Raizada, N152. My project is NavCert Offline: an Android app that scans maritime certificates, reads them, and answers questions about them, entirely on the phone, with the internet permission removed. Ten minutes: the problem, the app, how it is built, and a live demo."

### S02 · Problem · 45 s

- Kicker: `01 · THE PROBLEM`
- ★ **Critical papers, kept as crooked photos** (paradox)
  - Alt 1 (contrast): "Valid on paper, unreadable on a phone"
  - Alt 2 (question): "Why is the most important document the hardest to search?"
- On-slide text: three lines, each with a mono tag: "SKEWED: photographed at an angle, in bad light" / "SCATTERED: paper, scans, chat forwards" / "SILENT: no search, no expiry alert"
- Visual: x-embed `problem_photo.html` (≤ 16 KB): a synthetic certificate drawn in SVG tilts, gains a shadow gradient and blur over 3 s, then the three tags pop next to it. Palette: paper, ink, red for the "SILENT" tag only.
- Build: embed plays → tags 1, 2, 3 in order.
- Speaker notes (45 s): "Every seafarer and every ship runs on certificates: competency, medical fitness, safety, insurance. They must be valid, and someone must be able to read the dates. In practice these documents travel as paper originals, scanned PDFs and photos taken on a mess-room table, at an angle, in poor light. They are skewed, they are scattered across phones and email, and they are silent: you cannot search them and nothing tells you that one is about to expire. A generic scanner app fixes the picture but not the meaning. That is the gap I picked."

### S03 · Who hurts · 45 s

- Kicker: `02 · USER NEEDS`
- ★ **Who loses when a certificate can't be read?** (question)
  - Alt 1 (list of five): "Seafarers, agents, managers, inspectors, trainers"
  - Alt 2 (metaphor): "One document, five people waiting on it"
- On-slide text: five columns with a one-line need each. "Seafarer: keep my certificates valid and with me" / "Manning agent: verify a crew's documents fast" / "Ship manager: see expiry dates across a fleet" / "Inspector: read the certificate on board" / "Training institute: confirm what was issued". Bottom strip: "Constraints: weak connectivity at sea · identity data must stay private"
- Visual: five simple line icons (DM Mono labels) on mist background; bottom strip in ink with yellow text.
- Build: columns left to right → constraint strip last.
- Speaker notes (45 s): "Five groups touch these documents. The seafarer needs them valid to sign on. The manning agent checks a whole crew's certificates before deployment. The ship manager tracks vessel certificates and their expiry across a fleet. Inspectors read them on board. Training institutes issue them and answer verification calls. Two constraints cut across all of them: connectivity at sea and in many ports is limited, and these documents carry identity data, so uploading them to a cloud OCR service is a privacy risk. So the requirement became: everything on the device, nothing over the network." (Add one sourced fact from `docs/research/facts.md` here if available.)

### S04 · Solution · 40 s

- Kicker: `03 · THE APP`
- ★ **Camera + phone − internet = a certificate desk** (equation)
  - Alt 1 (imperative): "Photograph it once, never type it again"
  - Alt 2 (strikethrough): "~~Upload~~ Understand on the device"
- On-slide text: four verbs in a row: "SCAN · READ · EXTRACT · ASK" with one line under each: "Flatten and clean the page" / "Printed and handwritten text" / "Name, issue date, port, authority, expiry" / "Questions over saved documents"
- Visual: `05_certificate_fields.png` in a phone frame centred under the four verbs.
- Build: equation line → verbs one by one → phone.
- Speaker notes (40 s): "NavCert Offline does four things. Scan: it finds the page in the photo, flattens it and cleans it. Read: it recognises printed text, and handwriting through a second model. Extract: it pulls out the five fields people actually retype: certificate name, issue date, port of issue, issuing authority and expiry date. Ask: you can ask a question in plain English across your saved documents. No account, no server, and the network permission is removed from the manifest."

### S05 · Architecture · 60 s

- Kicker: `04 · HOW IT WORKS`
- ★ **An assembly line that fits in a pocket** (metaphor)
  - Alt 1 (number-led): "Eight stages, zero network calls"
  - Alt 2 (question): "What happens between the shutter and the answer?"
- On-slide text: the eight-box pipeline from section 3.2 with short labels: "Capture · Crop · Process (OpenCV) · OCR (ML Kit | PP-OCRv5) · Extract (rules → MiniLM → MobileBERT) · Review · Store (JSON + JPEG) · Ask". Under the line: "Dispatchers.IO for files · Dispatchers.Default for vision and ML · Compose state on the main thread"
- Visual: x-embed `pipeline_flow.html` (≤ 16 KB): eight boxes, a yellow dot travels left to right; at OCR the dot splits into two lanes and merges; dashed red frame labelled "NO INTERNET PERMISSION" draws itself around everything at the end.
- Build: boxes appear in sequence with the dot → threading caption → red frame.
- Speaker notes (60 s): "Architecturally it is one main activity hosting a Compose app, plus a crop activity reached through an explicit intent. Navigation is a Material 3 drawer with four destinations, and the scan flow is a three-step state machine: capture, preview, result. The camera uses the TakePicture contract with a FileProvider URI, so I need no camera permission. The bitmap is decoded on the IO dispatcher, OpenCV runs on the Default dispatcher, then ML Kit reads printed text; in handwriting mode each ML Kit line is re-read by PP-OCRv5 on ONNX Runtime. The extractor tries rules first, then my fine-tuned MiniLM, then MobileBERT. The user reviews and saves; each document is a JSON file plus a JPEG preview in app-private storage. The Ask screen chunks and ranks saved documents and runs MobileBERT on the best chunks. Every stage runs on the phone."

### S06 · Feature tour 1: capture to clean page · 45 s

- Kicker: `05 · FEATURE TOUR · SCAN`
- ★ **Straighten it before you read it** (imperative)
  - Alt 1 (before/after): "From mess-room photo to flat page"
  - Alt 2 (number-led): "Fifteen percent of the frame is enough to find the page"
- On-slide text: three captions under three phones: "Capture: camera or gallery, Auto / Printed / Handwriting" / "Crop: free crop and rotate" / "Preview: page detected, Auto · Clean · B&W"
- Visual: `01_home.png`, `03_crop.png`, `02_preview.png` side by side; under the third, a strip of `11_lab_edges.png → 12_lab_corners.png → 13_lab_warp.png` thumbnails from the Pipeline Lab.
- Build: phones left to right → lab strip last.
- Speaker notes (45 s): "The capture screen makes the camera the main action and keeps gallery import secondary. You choose Auto, Printed or Handwriting. If the frame is messy, the crop editor lets you crop and rotate; it is a third-party CropImageView hosted inside Compose with AndroidView. Then OpenCV takes over: edge detection, closing gaps, finding the largest four-cornered shape that covers at least fifteen percent of the frame, and warping it flat. The preview also checks if the photo is too dark, too bright or blurry, and offers three enhancement modes. The strip at the bottom is from the Pipeline Lab screen, which shows each of these stages on a synthetic certificate."

### S07 · Feature tour 2: text and fields · 45 s

- Kicker: `06 · FEATURE TOUR · READ`
- ★ **Five fields, lifted off the page** (number-led)
  - Alt 1 (contrast): "Fields, not just text"
  - Alt 2 (quote-style label): "Issued at Mumbai on … : found"
- On-slide text: left: "Editable text with highlights, notes and bookmark". Right: field table "Certificate · Issued · Port / place · Authority · Expires". Footnote line: "Rules first; ML only where the page shows evidence; abstains on non-certificates"
- Visual: `04_result_text.png` left, `05_certificate_fields.png` right, a yellow outline pulsing around the field card.
- Build: left phone → right phone → footnote.
- Speaker notes (45 s): "The result screen is a document editor, not a bare text box: line breaks are preserved, you can highlight phrases, add review notes and bookmark. Above it is the certificate summary with five fields. The extractor is hybrid: regular-expression rules handle labelled fields like 'issued at … on …' or 'valid until'. Only when a field is missing and the page shows evidence for it does it ask a question to my fine-tuned MiniLM model, and only then MobileBERT. If the text is not a certificate at all, it does not ask any model, so it does not invent dates. Everything stays editable because OCR can be wrong."

### S08 · Feature tour 3: library and ask · 40 s

- Kicker: `07 · FEATURE TOUR · KEEP & ASK`
- ★ **Search answers, not folders** (contrast)
  - Alt 1 (question): "When does this certificate expire? Ask the phone"
  - Alt 2 (imperative): "Ask your documents, offline"
- On-slide text: left caption "Library: search, folders, move, delete, thumbnails"; right caption "Ask: answer + source document, works with folder filter"
- Visual: `07_library.png` left, `06_ask_document.png` right.
- Build: left → right → answer text highlight in the right screenshot.
- Speaker notes (40 s): "Saved documents appear in the library with thumbnails loaded by Coil, a search box, and folders, so an agent can keep one folder per crew member or per vessel. The Ask screen takes a plain question, splits the saved documents into overlapping chunks, ranks them with TF-IDF weighting where title words count three times, and runs MobileBERT on the top four chunks. The answer comes back with the document it came from, so you can open the source and check it."

### S09 · New concepts · 55 s

- Kicker: `08 · BEYOND THE SYLLABUS`
- ★ **Twelve things the syllabus never covered** (number-led)
  - Alt 1 (metaphor): "Three engines, one phone"
  - Alt 2 (paradox): "Self-taught, then fine-tuned"
- On-slide text: 4×3 grid, each tile "concept · file": "ML Kit OCR · MainActivity" / "ONNX Runtime + PP-OCRv5 · PaddleHandwritingRecognizer" / "TFLite MobileBERT QA · OfflineDocumentAssistant" / "Fine-tuned int8 MiniLM · CertificateOnnxAnswerer" / "Own WordPiece tokenizer · CertificateOnnxAnswerer" / "OpenCV native pipeline · DocumentScanner" / "TF-IDF retrieval · DocumentKnowledgeIndex" / "Coroutines + dispatchers · MainActivity" / "Compose M3 adaptive drawer · MainActivity" / "FileProvider + Activity Result API · MainActivity" / "Cropper in AndroidView + Coil · CropEditorActivity" / "Python training pipeline · ml/"
- Visual: grid on mist background; the four ML tiles carry a blue left bar, the OpenCV tile yellow.
- Build: row by row.
- Speaker notes (55 s): "The rubric asks for three new concepts; here are twelve, each in a named file. Three on-device ML runtimes: ML Kit for printed text, ONNX Runtime running PP-OCRv5 for handwriting, where I wrote the line cropping, tensor packing and CTC decoding myself, and TensorFlow Lite running MobileBERT. I fine-tuned a MiniLM question-answering model on certificate text in Python, quantised it to int8 at 32.3 MiB, and wrote a WordPiece tokenizer in Kotlin to feed it. On the human-checked gold set it got 15 of 26 fields, against 4 of 26 for my first prototype. On the Android side: OpenCV native processing, a small retrieval engine, coroutines with the right dispatchers, an adaptive Material 3 drawer, the Activity Result API with FileProvider, and a cropper hosted in Compose."

### S10 · Play Store readiness & Material 3 · 45 s

- Kicker: `09 · STANDARDS`
- ★ **From ~~debug APK~~ to Play Store bundle** (strikethrough reveal)
  - Alt 1 (number-led): "449.8 MiB today, one download per device tomorrow"
  - Alt 2 (contrast): "Material on screen, minimal on disk"
- On-slide text: left column "Done": "Material 3 components and colour scheme · adaptive drawer ≥ 840 dp · target SDK 36 · no runtime permissions · network permissions removed · content descriptions on icon buttons". Right column "Release checklist": "App name + adaptive icon · R8 minify + resource shrink · App Bundle (per-ABI split) · models in an asset pack · privacy policy + Data safety: no data collected · backup excludes documents · dark theme + edge-to-edge"
- Visual: x-embed `apk_size_bars.html` (≤ 16 KB): horizontal stacked bar of the measured composition (native 4 ABIs ≈ 310, nlp models 128.3, dex 60.0, handwriting 7.6 MiB), then animates to "per device": native arm64 ≈ 67 + models moved to an asset pack. Labels in DM Mono; colours ink, blue, yellow, muted.
- Build: left column → bar animation → right column items tick in.
- Speaker notes (45 s): "On Material Design: every screen uses Material 3 components, a custom colour scheme and typography, and the drawer becomes permanent on tablets. Target SDK is 36, the app needs no runtime permissions, and network permissions are stripped. The honest part is size: the debug APK is 449.8 MiB. The bar shows why: four copies of native libraries for four CPU types, and two language models. An App Bundle ships only the phone's own CPU libraries, R8 shrinks the code, and the two models go into an asset pack. The remaining checklist is the store paperwork: name, icon, privacy policy and a Data safety form that can truthfully say no data is collected." (Update the right column ticks to reflect what is finished on presentation day.)

### S11 · Testing · 35 s

- Kicker: `10 · TESTING`
- ★ **Test the logic that never touches a pixel** (imperative)
  - Alt 1 (number-led): "Twelve unit tests, zero devices"
  - Alt 2 (question): "How do you test an app built on models?"
- On-slide text: table "Class · What it proves": "CertificateFieldExtractorTest (6) · rules, abstention, rejects bad dates" / "CertificateWordPieceTokenizerTest · token ids and offsets" / "DocumentKnowledgeIndexTest · retrieval picks the right document" / "DocumentFormattingTest · list formatting" / "OcrTextCorrectorTest (2) · safe word correction". Bottom: "Model quality measured separately in Python: 15/26 gold fields (MiniLM int8). Device check: OPPO CPH2665, Android 15."
- Visual: screenshot of the Gradle test report or Android Studio test runner (green), file `09_tests.png`.
- Build: table rows → bottom line.
- Speaker notes (35 s): "I split testing in two. Pure Kotlin logic, which is where the bugs hide, is covered by JUnit tests that run without a device: extraction rules, the extractor refusing to ask the model on non-certificates, rejecting truncated dates, the tokenizer, retrieval and OCR correction. Model quality is measured in Python against a human-checked gold set. The whole app was installed and run on an OPPO phone with Android 15. What I have not measured yet is end-to-end accuracy on photographed certificates; that is the next evaluation."

### S12 · Individual contribution · 45 s

- Kicker: `11 · MY CONTRIBUTION`
- ★ **App, models, data: one pair of hands** (list of three)
  - Alt 1 (before/after): "From 4/26 to 15/26 on the same gold set"
  - Alt 2 (metaphor): "Building the ship and the chart"
- On-slide text: timeline across the slide: "Aug: Compose app, camera, crop, OpenCV · PP-OCRv5 handwriting (10 Aug)" → "Early Sep: MobileBERT Q&A (2 Sep) · corpus prep: 108 PDFs, 351 pages" → "Sep: tiny-BERT 23.5% test EM → MiniLM fine-tune 57.7% gold → int8 export 32.3 MiB (23 Sep)" → "Oct: unit tests, Pipeline Lab, release prep". Right panel "Problems solved": "APK size · handwriting fallback · model file materialisation · weak labels vs gold set · network permission removal"
- Visual: horizontal timeline on mist; a small `git log --oneline` card (8 commits, layer by layer) in DM Mono at bottom right.
- Build: timeline left to right → problems panel → git card.
- Speaker notes (45 s): "This is a solo project, so every layer is mine: the Android app, the image pipeline, the model integration, and the Python data and training pipeline. The timeline comes from dated artefacts in the repo. The model story shows the iteration: my first tiny-BERT reached 23.5% exact match on test, so I did not trust it in the app; I moved to MiniLM, fine-tuned on certificate text, and reached 15 of 26 on a human-checked gold set, then quantised it to 32 MiB with the same gold score. On the right are the problems I had to solve along the way. The repository history is organised as eight commits, one per layer, and from here every change goes in as its own commit."

### S13 · Demo · 50 s

- Kicker: `12 · LIVE DEMO`
- ★ **Watch it read a certificate** (imperative)
  - Alt 1 (number-led): "One photo, five fields, one answer"
  - Alt 2 (question): "Will it read this page?"
- On-slide text: demo script as five small steps: "1 Scan the printed synthetic certificate · 2 Crop and preview · 3 Extract: fields appear · 4 Save to a folder · 5 Ask: 'When does this certificate expire?'" Fallback line in muted text: "Backup: recorded run".
- Visual: phone mirrored full-height on the right (scrcpy or Android Studio mirroring); left column holds the steps.
- Build: steps tick as performed.
- Speaker notes (50 s): "Live now. [Open camera, photograph the printed synthetic certificate.] The preview shows the page detected and flattened. [Tap Extract.] Text and the five fields appear. [Edit one word to show it is editable, save into folder 'Demo vessel'.] It is in the library. [Ask: When does this certificate expire?] The answer and its source document. Note the cloud-off icon: there is no network permission at all. If the camera misbehaves, I switch to the Pipeline Lab with the bundled sample, or to the recording."

### S14 · Roadmap & close · 30 s

- Kicker: `13 · NEXT`
- ★ **What if every seafarer carried the certificate desk?** (question)
  - Alt 1 (imperative): "Ship it, then remind them before it expires"
  - Alt 2 (equation): "Bundle + reminders + Room = release 1.0"
- On-slide text: three next steps: "Expiry reminders (WorkManager, local notifications)" / "Play release: App Bundle + model asset pack + privacy policy" / "Room database + end-to-end evaluation on photographed certificates". Closing line: "NavCert Offline · scan, read, ask, without the internet. Thank you."
- Visual: `05_certificate_fields.png` small at right with a yellow bell icon over the "Expires" row.
- Build: three steps → closing line.
- Speaker notes (30 s): "Three next steps. Turn the extracted expiry date into a local reminder, since expiry tracking is the one need every user group shares. Ship a proper Play release as an App Bundle with the models in an asset pack. And move metadata into Room and run a full evaluation on photographed certificates. NavCert Offline: scan, read and ask, without the internet. Thank you; happy to take questions."

---

## 6. Viva preparation (15 questions)

1. **Walk me through the activity lifecycle in your app.** `MainActivity.onCreate` initialises OpenCV (`OpenCVLoader.initLocal()`) and calls `setContent`. Compose state declared with `rememberSaveable` (destination, scan step, text, title, modes) survives rotation and process death through the saved-instance bundle. Bitmaps and model results are in `remember`, so they are rebuilt or lost on rotation; moving them to a `ViewModel` is the planned fix. Native objects are closed in `DisposableEffect.onDispose` when the composition leaves. `CropEditorActivity` is launched for a result and finishes with `RESULT_OK` + URI or `RESULT_CANCELED`.

2. **How does Compose state drive the UI?** Each piece of state is a `mutableStateOf`; reading it inside a composable subscribes that composable, and writing it triggers recomposition of only the readers. The scan flow is an enum (`ScanStep`), so the `when` in `OcrApp` decides which screen is on screen. State is hoisted to `OcrApp` and screens receive values plus callbacks (unidirectional data flow).

3. **Why offline-first instead of a cloud OCR API?** Connectivity at sea and in many ports is poor or costly, and certificates hold identity data. On-device models give predictable latency, zero running cost and no data transfer. The cost is APK size and phone CPU load, which I address with App Bundles and asset packs.

4. **How do you guarantee nothing goes online?** The manifest removes `INTERNET` and `ACCESS_NETWORK_STATE` with `tools:node="remove"`, overriding ML Kit's transitive request. Without `INTERNET` the OS blocks socket creation for the app. All models are in `assets/`.

5. **How are the models packaged and loaded?** As assets: MobileBERT `.tflite`, MiniLM int8 `.onnx`, PP-OCRv5 `.ort`, vocabularies. `noCompress` keeps the TFLite and text files uncompressed so they can be memory-mapped. ONNX Runtime needs a file path, so the ONNX models are copied once to `filesDir` with a byte-size check, then sessions are created lazily with full graph optimisation and 2+ intra-op threads, guarded by a lock.

6. **Why is the APK so large and how will you fix it?** The debug APK is 449.8 MiB. Measured composition: about 310 MiB of native libraries because four ABIs are packed, 128.3 MiB of language models, 60 MiB of unminified dex. Fix: App Bundle so each phone gets one ABI (≈ 67 MiB native for arm64), R8 minification, and the two QA models in a Play Asset Delivery pack.

7. **Which permissions do you request and why so few?** None at runtime. Camera capture uses the `TakePicture` contract, so the system camera app takes the photo into my `FileProvider` URI; gallery uses the system picker, which grants one-time access. No storage, camera or location permission is needed.

8. **What is a FileProvider and why use it?** It exposes a private file as a `content://` URI with temporary grant flags, so another app (the camera) can write to it without my app exposing a raw file path. Mine is limited to `cacheDir` via `res/xml/file_paths.xml`.

9. **How is data stored? Why not SQLite?** Each document is a JSON file plus a JPEG preview in `filesDir/documents`, folders in `folders.json`; writes go to a temp file and are renamed, so a crash cannot leave half a file. App-private storage needs no permission and is removed on uninstall. For a few hundred documents it is simple and fast. For queries, sorting and full-text search at scale, Room over SQLite is the right next step, and I would keep previews as files.

10. **How do you keep the UI responsive?** Coroutines: file I/O on `Dispatchers.IO`, OpenCV and model inference on `Dispatchers.Default`, results assigned back to Compose state on the main thread. ML Kit's `Task` callback starts the next coroutine. A progress state disables buttons while processing.

11. **What exactly does OpenCV do?** Scale to ≤ 1600 px for analysis, grayscale, Gaussian blur, Canny 55/170, 5×5 morphological close, find contours, keep the largest four-corner polygon covering ≥ 15% of the image, warp it to a rectangle at up to 3200 px, then enhance (CLAHE 2.2 with 8×8 tiles, median 3, adaptive threshold block 35 C 11 for B&W, unsharp mask, 18 px white border). It also flags dark (< 55), bright (> 220) and blurry (Laplacian variance < 65) photos.

12. **How does certificate field extraction work, and how accurate is it?** Rules first (labelled dates, "issued at … on …", authority headers); if a field is missing and the text shows evidence for it, ask the fine-tuned MiniLM; if ONNX fails, ask MobileBERT. Non-certificates are skipped entirely. MiniLM alone scored 15/26 on a six-document human-checked gold set (text input). The full photo-to-fields pipeline has not been measured end to end; that is the next evaluation, and the UI keeps every field editable.

13. **How did you test it?** 12 JUnit tests over pure Kotlin classes: extractor (6), tokenizer, retrieval, formatting, OCR correction (2). Model accuracy is evaluated in Python on a gold set. Manual end-to-end runs on an OPPO CPH2665 with Android 15. Instrumented UI tests are not written yet; Compose `ui-test-junit4` is already a dependency.

14. **How does the Ask feature work without a large language model?** Retrieval + extractive QA. Documents are split into 1,200-character chunks with 180 overlap, scored by term frequency × inverse document frequency (title matches ×3), the top 4 go to MobileBERT, which selects an answer span; the highest-logit span wins and is shown with its source document. It extracts, it does not generate, so it cannot invent text that is not in the document.

15. **How does your app follow Material Design and what is missing for the Play Store?** Material 3 `Scaffold`, `TopAppBar`, navigation drawer (modal on phones, permanent ≥ 840 dp), filter chips, outlined text fields, dialogs, Material icons, a custom M3 colour scheme and type scale, 48 dp touch targets. Missing for release: final name and icon, signed minified App Bundle, model asset pack, privacy policy and Data safety form, backup exclusion for documents, dark theme and edge-to-edge insets.

---

## 7. Source notes

- Code facts: files under `app/src/main/java/com/MAD/n152/`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/`.
- Model and dataset numbers: `ml/README.md`.
- APK composition: measured with Python `zipfile` on `app/build/outputs/apk/debug/app-debug.apk` (23 Sep build, 495,488,295 bytes). Current debug size 449.8 MiB as supplied by the project lead's latest build.
- Asset sizes and dates: file system listing of `app/src/main/assets/`.
- User-needs statements marked **[source]** await citations from `docs/research/facts.md`.
