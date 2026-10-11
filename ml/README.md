# Certificate OCR training data

`prepare_certificates.py` converts the supplied PDFs into 200-DPI page images, preserves any embedded text as bootstrap labels, removes exact duplicate pages from the split lists, and keeps every page from one PDF in the same train/validation/test split.

The supplied archive contains 108 PDFs (not 114): 351 pages total, 295 unique pages, and 56 exact duplicate pages. The unique-page split is 206 train, 56 validation, and 33 test pages.

```powershell
python ml/prepare_certificates.py --self-test
python ml/prepare_certificates.py
```

The generated `manifest.csv` is the dataset inventory. The next required step is OCR-assisted polygon and transcript annotation; embedded PDF text alone is not a trustworthy ground truth.

Bootstrap reviewable PP-OCRv6 labels and line crops:

```powershell
ml\.venv\Scripts\python.exe ml\bootstrap_ocr.py --self-test
ml\.venv\Scripts\python.exe ml\bootstrap_ocr.py
```

Files ending in `_pseudo.txt` are draft annotations. Correct them before fine-tuning; do not use the test split for training.

Open `data/bootstrap/review_queue.csv`, fill `corrected_text` for wrong predictions, and use `action=accept` or `action=reject` for low-confidence rows. Then generate the final recognition labels:

```powershell
ml\.venv\Scripts\python.exe ml\finalize_recognition_labels.py
```

Current bootstrap: 20,106 line crops, 18,166 accepted high-confidence draft labels, and 1,940 low-confidence lines awaiting review. Manually verify the entire test split before reporting model accuracy.

Certificate-field prototype (engineering drawings excluded):

```powershell
ml\.venv\Scripts\python.exe ml\prepare_certificate_qa.py
ml\.venv\Scripts\python.exe ml\train_certificate_qa.py --epochs 30
ml\.venv\Scripts\python.exe ml\export_certificate_qa.py
```

This produces weakly labeled QA samples for certificate name, issue date, issue port/place, issuing authority, and expiry date. The current tiny-BERT checkpoint reaches 35.3% validation and 23.5% test exact-span accuracy, so the Android demo retains the stronger bundled MobileBERT plus deterministic field extraction instead of trusting this prototype alone.

## Expanded certificate QA experiment

`prepare_augmented_qa.py` adds the GENESIS ALPHA and FAIRMACS NICOBAR archives. It filters likely non-certificates and unusable PDF text, uses on-device-style OCR for scanned first pages, and keeps a six-document, 26-field human-checked test set out of training. The remaining labels are automatic and can be wrong; they are not ground truth. Split by source PDF, and verify no duplicate page text crosses splits.

```powershell
ml\.venv\Scripts\python.exe ml\prepare_certificate_qa.py --self-test
ml\.venv\Scripts\python.exe ml\prepare_augmented_qa.py --include-scanned
ml\.venv\Scripts\python.exe ml\train_certificate_qa.py --data ml\data\certificate_qa_augmented --output ml\models\certificate-minilm-augmented --base-model deepset/minilm-uncased-squad2 --epochs 8
ml\.venv\Scripts\python.exe ml\evaluate_certificate_qa.py --model ml\models\certificate-minilm-augmented --data ml\data\certificate_qa_augmented\gold_test.jsonl
```

Results: 169 weak training rows and 49 weak validation rows from 77 and 28 source PDFs, respectively. Across these rows, 111 came from the original OCR pool, 43 from new PDF text, and 64 from OCR of scanned pages in the two new archives. Training aligned 160 and 47 of those rows to tokenizer spans. The best MiniLM checkpoint was epoch 6. It scored 35/49 (71.4%) exact answers on weak validation, and 15/26 (57.7%) on the six-document, human-checked gold set. The prior tiny model scored 4/26 (15.4%) on the same gold set. Gold breakdown: certificate name 2/6, issue date 3/6, issue port 4/4, issuing authority 2/4, expiry date 4/6. These are pilot figures with wide uncertainty, not a claim of 57.7% performance on all certificates.

The trained QA model reads *text*, not pixels. Its scores do not include camera capture, document cropping, OCR errors, or missing-field abstention. The 32.3 MiB int8 ONNX export scored 33/49 (67.3%) on weak validation and 15/26 on gold, but changed three individual gold predictions compared with the 126.9 MiB full-precision export. Do not replace the Android model without measuring the full photographed-certificate pipeline and checking mobile inference cost.

## September 2026 on-device research and implementation choice

| Approach | What the primary source supports | Decision for this app |
| --- | --- | --- |
| [LayoutLMv3 paper](https://arxiv.org/html/2204.08387v3) and [code](https://github.com/microsoft/unilm/tree/master/layoutlmv3) | Joint text, page-image and 2D-position modeling; base model has 133M parameters and published document benchmarks. | Valuable future layout-aware training baseline, but much larger than the current quantized QA model and would require word-box annotations. Published benchmark scores are not certificate scores. |
| [Donut paper](https://arxiv.org/abs/2111.15664) and [code](https://github.com/clovaai/donut) | OCR-free image-to-structured-output document understanding. | Promising when OCR is the dominant error, but a new image-to-text model and more gold image/JSON pairs are needed; not a measured drop-in Android upgrade. |
| [PP-OCRv6 release](https://huggingface.co/blog/PaddlePaddle/pp-ocrv6) and [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR) | Small tier has 7.7M parameters and ONNX availability, with published general OCR benchmarks. | Next controlled OCR challenger against the bundled ML Kit recognizer, especially for scanned/blurred print. Do not substitute its general benchmark for our certificate accuracy. |
| [ML Kit Android text recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) | Bundled Latin model runs without first-use download; input resolution and focus affect recognition. | Keep as the current offline primary OCR and layout detector until a photographed-certificate test proves an alternative better. |
| [MiniLM extractive QA model](https://huggingface.co/deepset/minilm-uncased-squad2) | English SQuAD 2.0 checkpoint with 384-token context windows; CC BY 4.0. | Fine-tuned on our certificate text and integrated into Android as the 32.3 MiB int8 ONNX fallback for fields not found by explicit labels/rules. Same WordPiece vocabulary as the existing app asset. |

The Android app now skips certificate-field extraction on unrelated text, uses deterministic field extraction first, then the certificate-specific ONNX model for missing fields, then its existing MobileBERT as a fallback if ONNX is unavailable. Document-wide Q&A remains on the existing MobileBERT path. Unit tests and a debug build pass, but no phone was connected for an end-to-end runtime or latency test. The 57.7% pilot result is the text-only MiniLM result, **not** a measured score for the new hybrid app pipeline. The next meaningful evaluation is a fresh, vessel-disjoint set of photographed certificates with five manually verified fields, including documents where a field is absent. Report per-field precision/recall, whole-certificate accuracy, OCR CER/WER, and median/p95 latency on the actual phone.
