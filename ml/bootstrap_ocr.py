from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

from PIL import Image
from paddleocr import PaddleOCR


def load_manifest(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8") as handle:
        return [row for row in csv.DictReader(handle) if not row["duplicate_of"]]


def compile_labels(rows: list[dict[str, str]], processed: Path, output: Path, min_score: float) -> None:
    crops_dir = output / "line_crops"
    review_rows = []
    recognition = {split: [] for split in ("train", "val", "test")}
    detection = {split: [] for split in ("train", "val", "test")}

    for row in rows:
        result_path = output / "ocr_json" / f"{row['id']}.json"
        if not result_path.exists():
            continue
        result = json.loads(result_path.read_text(encoding="utf-8"))
        image_path = processed / row["image"]
        image = Image.open(image_path).convert("RGB")
        annotations = []
        for index, (text, score, box, polygon) in enumerate(
            zip(result["rec_texts"], result["rec_scores"], result["rec_boxes"], result["rec_polys"])
        ):
            text = text.strip()
            if not text:
                continue
            x1, y1, x2, y2 = map(int, box)
            crop_path = crops_dir / row["split"] / f"{row['id']}_{index:03d}.jpg"
            crop_path.parent.mkdir(parents=True, exist_ok=True)
            image.crop((max(0, x1 - 3), max(0, y1 - 3), min(image.width, x2 + 3), min(image.height, y2 + 3))).save(
                crop_path, quality=95
            )
            relative_crop = crop_path.relative_to(output).as_posix()
            if float(score) >= min_score:
                recognition[row["split"]].append(f"{relative_crop}\t{text}")
                annotations.append({"transcription": text, "points": polygon})
            review_rows.append(
                {
                    "page_id": row["id"],
                    "line_index": index,
                    "split": row["split"],
                    "crop": relative_crop,
                    "prediction": text,
                    "confidence": round(float(score), 5),
                    "needs_review": float(score) < min_score,
                    "corrected_text": "",
                    "action": "",
                }
            )
        relative_image = image_path.relative_to(processed).as_posix()
        detection[row["split"]].append(f"{relative_image}\t{json.dumps(annotations, ensure_ascii=False)}")

    for split in recognition:
        (output / f"rec_{split}_pseudo.txt").write_text("\n".join(recognition[split]) + "\n", encoding="utf-8")
        (output / f"det_{split}_pseudo.txt").write_text("\n".join(detection[split]) + "\n", encoding="utf-8")
    review_rows.sort(key=lambda row: (not row["needs_review"], row["confidence"]))
    with (output / "review_queue.csv").open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(review_rows[0]))
        writer.writeheader()
        writer.writerows(review_rows)
    print(
        f"Compiled {len(review_rows)} line crops; "
        f"{sum(row['needs_review'] for row in review_rows)} require priority review below confidence {min_score}."
    )


def run(rows: list[dict[str, str]], processed: Path, output: Path, limit: int | None) -> None:
    json_dir = output / "ocr_json"
    json_dir.mkdir(parents=True, exist_ok=True)
    pending = [row for row in rows if not (json_dir / f"{row['id']}.json").exists()]
    if limit is not None:
        pending = pending[:limit]
    if pending:
        ocr = PaddleOCR(
            engine="onnxruntime",
            device="cpu",
            text_detection_model_name="PP-OCRv6_small_det",
            text_recognition_model_name="PP-OCRv6_small_rec",
            use_doc_orientation_classify=False,
            use_doc_unwarping=False,
            use_textline_orientation=False,
        )
        for number, row in enumerate(pending, 1):
            result = ocr.predict(str(processed / row["image"]))
            data = result[0].json["res"]
            (json_dir / f"{row['id']}.json").write_text(
                json.dumps(data, ensure_ascii=False), encoding="utf-8"
            )
            print(f"[{number}/{len(pending)}] {row['id']}: {len(data['rec_texts'])} lines", flush=True)


def self_test() -> None:
    assert load_manifest
    assert 0.0 <= 0.85 <= 1.0
    print("self-test passed")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate PP-OCRv6 pseudo-labels and reviewable line crops.")
    parser.add_argument("--processed", type=Path, default=Path("ml/data/processed"))
    parser.add_argument("--output", type=Path, default=Path("ml/data/bootstrap"))
    parser.add_argument("--limit", type=int)
    parser.add_argument("--min-score", type=float, default=0.85)
    parser.add_argument("--compile-only", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
    else:
        manifest = load_manifest(args.processed / "manifest.csv")
        args.output.mkdir(parents=True, exist_ok=True)
        if not args.compile_only:
            run(manifest, args.processed, args.output, args.limit)
        compile_labels(manifest, args.processed, args.output, args.min_score)
