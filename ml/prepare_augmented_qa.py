"""Build a certificate-only QA set from the two new vessel archives.

PDF text and existing OCR labels are weak supervision. The small gold set below
was checked against the actual certificates and is never added to training.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import zipfile
from collections import Counter
from pathlib import Path

import pymupdf

from prepare_certificate_qa import EXCLUDE, QUESTIONS, clean, extract


CERTIFICATE_SOURCE = re.compile(r"certificate|attestation|licen[cs]e|registry|registration|insurance", re.I)


GOLD = {
    ("GENESIS ALPHA", "INTERNATIONAL_SHIP_SECURITY_CERTIFICATE.pdf"): {
        "certificate_name": "INTERNATIONAL SHIP SECURITY CERTIFICATE",
        "issue_date": "19/05/2025", "issue_port": "MUMBAI",
        "issuing_authority": "Indian Register of Shipping", "expiry_date": "08/05/2030",
    },
    ("GENESIS ALPHA", "MARITIME_LABOUR_CERTIFICATE.pdf"): {
        "certificate_name": "MARITIME LABOUR CERTIFICATE",
        "issue_date": "19/05/2025", "issue_port": "MUMBAI",
        "issuing_authority": "Indian Register of Shipping", "expiry_date": "08/05/2030",
    },
    ("GENESIS ALPHA", "CERTIFICATE_OF_INSURANCE__P_I_.pdf"): {
        "certificate_name": "CERTIFICATE OF INSURANCE",
        "issue_date": "29 Apr 2026", "expiry_date": "29 Apr 2027",
    },
    ("FAIRMACS NICOBAR", "Certificate_Of_Class.pdf"): {
        "certificate_name": "Certificate Of Class",
        "issue_date": "02/01/2024", "issue_port": "Mumbai",
        "issuing_authority": "Indian Register of Shipping", "expiry_date": "19/04/2027",
    },
    ("FAIRMACS NICOBAR", "Certificate_of_Insurance__Removal_of_Wrecks_.pdf"): {
        "certificate_name": "CERTIFICATE OF INSURANCE OR OTHER FINANCIAL SECURITY IN RESPECT OF\nLIABILITY FOR THE REMOVAL OF WRECKS",
        "issue_date": "05-NOV-2025", "issue_port": "Mumbai",
        "issuing_authority": "Government of India", "expiry_date": "26-OCT-2026",
    },
    ("FAIRMACS NICOBAR", "Certificate_of_Insurance__P_I_.pdf"): {
        "certificate_name": "CERTIFICATE OF INSURANCE",
        "issue_date": "27th October 2025", "expiry_date": "26th October 2026",
    },
}


def group_key(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", Path(name).stem.lower())


def usable_text(text: str) -> bool:
    return len(text) >= 200 and sum(char.isascii() and char.isalpha() for char in text) / len(text) >= 0.45


def useful_value(field: str, value: str) -> bool:
    if field == "certificate_name":
        return (len(value) >= 15
                and value.lower() not in {"certificate of insurance or other financial security in respect of"}
                and not re.search(r"\b(?:of|the|and|for|on|to)$", value, re.I)
                and not re.search(r"\b(which|to which|the following|verification of|may be|shall be)\b", value, re.I))
    if field == "issue_port":
        return 2 <= len(value) <= 45 and not re.search(r"signature|stamp|certificate|society|issue", value, re.I)
    if field == "issuing_authority":
        return (4 <= len(value) <= 100
                and not re.search(r"^(the|this|of|flag administration|director)$|society or its|available on board|\b(?:of|for|the)$", value, re.I))
    if field in {"issue_date", "expiry_date"}:
        return bool(re.search(r"(?:^|\D)\d{1,2}(?:st|nd|rd|th)?(?:\b|[./-])", value, re.I))
    return True


def qa_row(identifier: str, source: str, field: str, context: str, value: str, provenance: str) -> dict:
    start = context.casefold().find(value.casefold())
    if start < 0:
        raise ValueError(f"Answer not present in context: {identifier} {field}: {value!r}")
    return {
        "id": f"{identifier}_{field}", "source_pdf": source, "field": field,
        "question": QUESTIONS[field], "context": context,
        "answers": {"text": [context[start : start + len(value)]], "answer_start": [start]},
        "provenance": provenance,
    }


def old_ocr_score(row: dict, ocr_dir: Path) -> float:
    page_id = row["id"][: -len(row["field"]) - 1]
    path = ocr_dir / f"{page_id}.json"
    if not path.exists():
        return 0.0
    data = json.loads(path.read_text(encoding="utf-8"))
    value = row["answers"]["text"][0].casefold()
    return max((float(score) for text, score in zip(data["rec_texts"], data["rec_scores"])
                if value in text.casefold()), default=0.0)


def ocr_first_page(pdf, image_path: Path, cache_path: Path, engine) -> tuple[str, dict[str, float]]:
    if cache_path.exists():
        data = json.loads(cache_path.read_text(encoding="utf-8"))
    else:
        from PIL import Image

        image_path.parent.mkdir(parents=True, exist_ok=True)
        pixmap = pdf[0].get_pixmap(dpi=170, colorspace=pymupdf.csRGB, alpha=False)
        Image.frombytes("RGB", (pixmap.width, pixmap.height), pixmap.samples).save(image_path, quality=90)
        data = engine.predict(str(image_path))[0].json["res"]
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        cache_path.write_text(json.dumps({"rec_texts": data["rec_texts"], "rec_scores": data["rec_scores"]},
                                         ensure_ascii=False), encoding="utf-8")
    lines = [clean(line) for line in data["rec_texts"]]
    return "\n".join(lines), {line.casefold(): float(score) for line, score in zip(lines, data["rec_scores"])}


def build(archives: list[Path], old_train: Path, old_val: Path, old_ocr_dir: Path,
          output: Path, include_scanned: bool) -> None:
    splits: dict[str, list[dict]] = {"train": [], "val": [], "gold_test": []}
    excluded_groups = {group_key(name) for _, name in GOLD}
    seen_pages: set[str] = set()
    counts = Counter()

    for split, path in (("train", old_train), ("val", old_val)):
        for row in map(json.loads, filter(None, path.read_text(encoding="utf-8").splitlines())):
            value = row["answers"]["text"][0]
            if (group_key(row["source_pdf"]) in excluded_groups
                or not CERTIFICATE_SOURCE.search(row["source_pdf"])
                or EXCLUDE.search(row["source_pdf"])
                or not useful_value(row["field"], value)
                or old_ocr_score(row, old_ocr_dir) < 0.85):
                counts["old_rows_filtered"] += 1
                continue
            row["provenance"] = "old_ocr_pseudo_label"
            splits[split].append(row)

    gold_seen: set[tuple[str, str]] = set()
    ocr_engine = None
    for archive in archives:
        vessel = archive.stem.replace("_Certificates", "")
        with zipfile.ZipFile(archive) as zipped:
            # Read the gold documents first so aliases with identical content cannot
            # enter training or validation under a different filename.
            infos = sorted(zipped.infolist(), key=lambda item: (GOLD.get((vessel, Path(item.filename).name)) is None, item.filename))
            for info in infos:
                name = Path(info.filename).name
                if info.is_dir() or not name.lower().endswith(".pdf"):
                    continue
                gold_fields = GOLD.get((vessel, name))
                if not gold_fields and (not CERTIFICATE_SOURCE.search(name) or EXCLUDE.search(name)):
                    counts["non_certificate_pdf_skipped"] += 1
                    continue
                identifier = hashlib.sha256(f"{vessel}/{name}".encode()).hexdigest()[:16]
                line_scores: dict[str, float] = {}
                provenance = "pdf_text_weak_label"
                with pymupdf.open(stream=zipped.read(info), filetype="pdf") as pdf:
                    context = pdf[0].get_text("text").strip()
                    if not usable_text(context) and include_scanned and gold_fields is None:
                        if ocr_engine is None:
                            from paddleocr import PaddleOCR

                            ocr_engine = PaddleOCR(
                                engine="onnxruntime", device="cpu",
                                text_detection_model_name="PP-OCRv6_small_det",
                                text_recognition_model_name="PP-OCRv6_small_rec",
                                use_doc_orientation_classify=False, use_doc_unwarping=False,
                                use_textline_orientation=False,
                            )
                        context, line_scores = ocr_first_page(
                            pdf, output / "ocr_images" / f"{identifier}.jpg",
                            output / "ocr_cache" / f"{identifier}.json", ocr_engine,
                        )
                        provenance = "scanned_page_ocr_pseudo_label"
                if not usable_text(context):
                    counts["unusable_text_layer_skipped"] += 1
                    continue
                fingerprint = hashlib.sha256(re.sub(r"\s+", " ", context).casefold().encode()).hexdigest()
                source = f"{vessel}/{name}"
                if gold_fields is not None:
                    gold_seen.add((vessel, name))
                    seen_pages.add(fingerprint)
                    for field, value in gold_fields.items():
                        splits["gold_test"].append(qa_row(identifier, source, field, context, value, "human_checked_pdf"))
                    continue
                if group_key(name) in excluded_groups:
                    counts["gold_template_excluded"] += 1
                    continue
                if fingerprint in seen_pages:
                    counts["duplicate_text_skipped"] += 1
                    continue
                seen_pages.add(fingerprint)
                labels = extract([clean(line) for line in context.splitlines() if clean(line)])
                split = "val" if int(hashlib.sha256(group_key(name).encode()).hexdigest()[:8], 16) % 2 == 0 else "train"
                for field, value in labels.items():
                    if not useful_value(field, value):
                        counts["weak_field_filtered"] += 1
                        continue
                    if line_scores and max((score for line, score in line_scores.items() if value.casefold() in line), default=0.0) < 0.85:
                        counts["low_confidence_ocr_field_skipped"] += 1
                        continue
                    try:
                        splits[split].append(qa_row(identifier, source, field, context, value, provenance))
                    except ValueError:
                        counts["unmatched_answer_skipped"] += 1
    if gold_seen != set(GOLD):
        raise ValueError(f"Gold documents missing: {set(GOLD) - gold_seen}")
    if not splits["train"] or not splits["val"] or not splits["gold_test"]:
        raise ValueError("An empty split would make the evaluation invalid")
    output.mkdir(parents=True, exist_ok=True)
    for split, rows in splits.items():
        (output / f"{split}.jsonl").write_text(
            "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8"
        )
        counts[f"{split}_rows"] = len(rows)
        counts[f"{split}_documents"] = len({row['source_pdf'] for row in rows})
        print(split, len(rows), dict(Counter(row["field"] for row in rows)))
    train_sources = {row["source_pdf"] for row in splits["train"]}
    val_sources = {row["source_pdf"] for row in splits["val"]}
    gold_sources = {row["source_pdf"] for row in splits["gold_test"]}
    assert not (train_sources & val_sources or train_sources & gold_sources or val_sources & gold_sources)
    (output / "summary.json").write_text(json.dumps(dict(counts), indent=2), encoding="utf-8")
    print(dict(counts))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--archives", nargs=2, type=Path, default=[
        Path.home() / "Downloads/GENESIS ALPHA_Certificates.zip",
        Path.home() / "Downloads/FAIRMACS NICOBAR_Certificates.zip",
    ])
    parser.add_argument("--old-train", type=Path, default=Path("ml/data/certificate_qa/train.jsonl"))
    parser.add_argument("--old-val", type=Path, default=Path("ml/data/certificate_qa/val.jsonl"))
    parser.add_argument("--old-ocr-dir", type=Path, default=Path("ml/data/bootstrap/ocr_json"))
    parser.add_argument("--output", type=Path, default=Path("ml/data/certificate_qa_augmented"))
    parser.add_argument("--include-scanned", action="store_true")
    args = parser.parse_args()
    build(args.archives, args.old_train, args.old_val, args.old_ocr_dir, args.output, args.include_scanned)
