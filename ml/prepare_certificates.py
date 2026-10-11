from __future__ import annotations

import argparse
import csv
import hashlib
from pathlib import Path

import pymupdf


def split_for(name: str) -> str:
    bucket = int(hashlib.sha256(name.encode("utf-8")).hexdigest()[:8], 16) % 20
    return "test" if bucket < 2 else "val" if bucket < 5 else "train"


def long_path(path: Path) -> str:
    resolved = str(path.resolve())
    return resolved if resolved.startswith("\\\\?\\") else "\\\\?\\" + resolved


def prepare(source: Path, output: Path, dpi: int) -> None:
    pages_dir = output / "pages"
    text_dir = output / "bootstrap_text"
    pages_dir.mkdir(parents=True, exist_ok=True)
    text_dir.mkdir(parents=True, exist_ok=True)

    rows = []
    seen_hashes: dict[str, str] = {}
    for pdf_index, pdf_path in enumerate(sorted(source.glob("*.pdf")), 1):
        with open(long_path(pdf_path), "rb") as handle:
            document = pymupdf.open(stream=handle.read(), filetype="pdf")
        split = split_for(pdf_path.name)
        for page_index, page in enumerate(document):
            sample_id = f"cert_{pdf_index:03d}_p{page_index + 1:03d}"
            image_path = pages_dir / f"{sample_id}.jpg"
            pixmap = page.get_pixmap(dpi=dpi, colorspace=pymupdf.csRGB, alpha=False)
            pixmap.save(str(image_path), jpg_quality=95)
            digest = hashlib.sha256(image_path.read_bytes()).hexdigest()
            text = page.get_text("text").strip()
            (text_dir / f"{sample_id}.txt").write_text(text, encoding="utf-8")
            rows.append(
                {
                    "id": sample_id,
                    "source_pdf": pdf_path.name,
                    "page": page_index + 1,
                    "page_count": len(document),
                    "split": split,
                    "image": image_path.relative_to(output).as_posix(),
                    "width": pixmap.width,
                    "height": pixmap.height,
                    "text_chars": len(text),
                    "has_text_layer": bool(text),
                    "sha256": digest,
                    "duplicate_of": seen_hashes.get(digest, ""),
                }
            )
            seen_hashes.setdefault(digest, sample_id)
        document.close()

    fields = list(rows[0])
    with (output / "manifest.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)
    for split in ("train", "val", "test"):
        ids = [row["id"] for row in rows if row["split"] == split and not row["duplicate_of"]]
        (output / f"{split}.txt").write_text("\n".join(ids) + "\n", encoding="utf-8")

    unique = sum(not row["duplicate_of"] for row in rows)
    text_pages = sum(row["has_text_layer"] for row in rows)
    print(f"Prepared {len(rows)} pages ({unique} unique, {text_pages} with embedded text) from {pdf_index} PDFs.")


def self_test() -> None:
    assert split_for("example.pdf") == split_for("example.pdf")
    assert {split_for(f"certificate-{index}.pdf") for index in range(100)} == {"train", "val", "test"}
    print("self-test passed")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Render certificate PDFs into a document-level train/val/test dataset.")
    parser.add_argument("--source", type=Path, default=Path("ml/data/raw/DRA_1"))
    parser.add_argument("--output", type=Path, default=Path("ml/data/processed"))
    parser.add_argument("--dpi", type=int, default=200)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
    else:
        prepare(args.source, args.output, args.dpi)
