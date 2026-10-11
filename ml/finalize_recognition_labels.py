from __future__ import annotations

import argparse
import csv
from pathlib import Path


def resolved_text(row: dict[str, str]) -> str | None:
    action = row["action"].strip().lower()
    corrected = row["corrected_text"].strip()
    needs_review = row["needs_review"].lower() == "true"
    if action == "reject" or (needs_review and not corrected and action != "accept"):
        return None
    return corrected or row["prediction"].strip() or None


def finalize(queue: Path, output: Path) -> None:
    labels = {split: [] for split in ("train", "val", "test")}
    skipped = 0
    with queue.open(encoding="utf-8-sig") as handle:
        for row in csv.DictReader(handle):
            text = resolved_text(row)
            if text is None:
                skipped += 1
                continue
            labels[row["split"]].append(f"{row['crop']}\t{text}")
    output.mkdir(parents=True, exist_ok=True)
    for split, lines in labels.items():
        (output / f"rec_{split}.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Finalized {sum(map(len, labels.values()))} labels; skipped {skipped} unresolved or rejected lines.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Apply human corrections to recognition pseudo-labels.")
    parser.add_argument("--queue", type=Path, default=Path("ml/data/bootstrap/review_queue.csv"))
    parser.add_argument("--output", type=Path, default=Path("ml/data/final"))
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        base = {"prediction": "draft", "corrected_text": "", "action": "", "needs_review": "True"}
        assert resolved_text(base) is None
        assert resolved_text({**base, "action": "accept"}) == "draft"
        assert resolved_text({**base, "corrected_text": "fixed"}) == "fixed"
        assert resolved_text({**base, "action": "reject"}) is None
        print("Self-test passed.")
    else:
        finalize(args.queue, args.output)
