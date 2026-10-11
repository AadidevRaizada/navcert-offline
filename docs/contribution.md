# Individual contribution: NavCert Offline

Aadidev Raizada (N152), solo project. Every layer is my own work: the Android app, the OpenCV pipeline, the model integration, the Python data and training pipeline, the synthetic test data, the evaluations, the decks and the paper.

## How the dates were derived

The git history does not show the timeline. The work was committed as logical layers on 12 Oct 2026, so every early commit carries that date. The dates below come from file modification times in the repository and from the `ml/models` and `ml/data` folder listings, read on 12 Oct 2026. A file's date is the last time it was saved, so it marks when that piece reached its current form, not when work on it started. Files edited again on 12 Oct (most Kotlin sources) lost their earlier dates and are not used as evidence.

## Timeline

| Date | Evidence (file or folder date) | What was built |
| --- | --- | --- |
| 14 Jul 2026 | `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, Gradle wrapper, template tests; `app/` folder created | Android Studio project with the Kotlin + Jetpack Compose toolchain |
| 20 Jul 2026 | `app/build/` folder created | First app builds |
| 3 Aug 2026 | `res/xml/file_paths.xml` | Camera capture through `FileProvider` (no camera permission) |
| 7 Aug 2026 | `PRODUCT.md` | Product and design brief |
| 10 Aug 2026 | `PaddleHandwritingRecognizer.kt`, `DocumentTextFormatter.kt`, `assets/handwriting/en_PP-OCRv5_mobile_rec.ort`, its dictionary and `NOTICE.txt` | Handwriting recognition with PP-OCRv5 on ONNX Runtime (line cropping, tensor packing, CTC decoding); layout reconstruction from ML Kit boxes |
| 2 Sep 2026 | `DocumentRepository.kt`, `assets/nlp/mobilebert_qa.tflite`, `assets/nlp/vocab.txt`, `res/values/colors.xml`, `themes.xml`, `DocumentFormattingTest.kt`, `DocumentKnowledgeIndexTest.kt` | Local document library (JSON + JPEG, atomic writes); offline Q&A with MobileBERT and TF-IDF-style retrieval; app theme; first unit tests |
| 21 Sep 2026 | `ml/data/raw` (12:29), `ml/data/processed` (12:40), `ml/data/bootstrap` (12:42), `ml/data/final` (12:56), `ml/data/certificate_qa` (14:19), `ml/models/certificate-bert-tiny` (14:37); `ml/prepare_certificates.py`, `bootstrap_ocr.py`, `finalize_recognition_labels.py` | Certificate corpus preparation (108 PDFs, 351 pages, document-level splits), PP-OCR bootstrap labels (20,106 line crops), weak QA labels, BERT-tiny prototype (23.5% test exact match) |
| 23 Sep 2026 | `ml/models/certificate-bert-tiny-augmented` (09:56), `ml/data/certificate_qa_augmented` (11:17), `ml/models/certificate-minilm-augmented` (11:15); `prepare_augmented_qa.py`, `train_certificate_qa.py`, `evaluate_certificate_qa.py`, `export_certificate_qa.py`, `ml/README.md`; `assets/nlp/certificate_minilm.int8.onnx`, `MODEL_ATTRIBUTION.txt`; `CertificateOnnxAnswerer.kt`, `OfflineDocumentAssistant.kt`, `CertificateWordPieceTokenizerTest.kt` | Augmented dataset with a six-document human-checked gold set; MiniLM fine-tune (15/26 gold vs 4/26 for BERT-tiny); 32.3 MiB int8 ONNX export; Kotlin WordPiece tokenizer and ONNX answerer; hybrid rules → MiniLM → MobileBERT field extraction |
| 12 Oct 2026 | Git history (all commits); `docs/rubrics/`, `docs/research/`, `ml/ai_eval/`, `ml/ivp_eval/`, `tools/make_sample_certificate.py`, `assets/lab/`, `PipelineLabScreen.kt`, `IvpOperations.kt`, `presentations/`, `paper/` | Repository organised into commits; course mappings; AI experiments (CSP, search, decision tree); paired IVP evaluation on 200 synthetic captures; synthetic sample certificate; Pipeline Lab screen; decks and paper; final polish (below) |

## Final polish, 12 Oct 2026

Each item is its own commit:

- OCR correction: digit look-alikes (0/1/5/6/8) are now corrected inside words while identifiers, certificate numbers, dates and ordinals stay protected; maritime vocabulary added so "Manning" is no longer changed to "Scanning". Both bugs were found by the `ml/ai_eval` search experiment and are reproduced by unit tests.
- Port of issue: tolerant to "Part/P0rt/Pori of issue", handles a value printed above its label, and prefers a real port over repeating the issuing authority. On the sample certificate the Port field now reads "Port of Valmora".
- Crop editor respects system-bar insets.
- Play Store readiness: leftover lab exercise code removed, backup disabled and documents excluded from cloud backup and device transfer, R8 minification and resource shrinking for release (verified on the emulator), adaptive launcher icon, Privacy & offline screen and `docs/PRIVACY.md`.

## Problems solved along the way

| Problem | What I did |
| --- | --- |
| ML Kit pulls in network permissions | Removed `INTERNET` and `ACCESS_NETWORK_STATE` with `tools:node="remove"` in the manifest. |
| ONNX Runtime needs a file path, not an asset stream | Copy the model to `filesDir` once with a size check, then create the session lazily under a lock. |
| Handwriting model can fail or be unsure | Fall back to ML Kit text when PP-OCRv5 fails or its confidence is low. |
| Weak labels make validation look better than reality | Kept a six-document human-checked gold set out of training and report the gold number (15/26), not the weak-validation number (71.4%). |
| Leakage between pages of the same certificate | Split by source PDF, removed duplicate pages by hash and duplicate text by fingerprint. |
| Model invents dates on non-certificates | Certificate gate before extraction; model answers validated as real dates. |
| APK size | Measured the composition (native libraries for four ABIs and two language models dominate); enabled R8 and resource shrinking; App Bundle and an asset pack are the next step. |
| Demo without private data | Built a synthetic certificate generator and bundled the sample in the Pipeline Lab. |
