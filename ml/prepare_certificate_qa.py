from __future__ import annotations

import argparse
import csv
import json
import re
from pathlib import Path


MONTH = r"(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)"
DATE = re.compile(
    rf"(?:\d{{1,2}}[./-]\d{{1,2}}[./-]\d{{2,4}}|\d{{1,2}}(?:st|nd|rd|th)?\s+{MONTH},?\s+\d{{4}}|{MONTH}\.?\s+\d{{1,2}},?\s+\d{{4}}|{MONTH}[- ]\d{{4}})",
    re.I,
)
INCLUDE = re.compile(r"certificate|attestation|licen[cs]e|registry|registration|insurance|inspection|test|receipt|survey", re.I)
EXCLUDE = re.compile(r"drawing|layout|seating|arrangement|schematic|specification|manual|datasheet|calculation|plan", re.I)
QUESTIONS = {
    "certificate_name": "What is the full name or title of this certificate?",
    "issue_date": "What is the date of issue of this certificate?",
    "issue_port": "At which port or place was this certificate issued?",
    "issuing_authority": "Which authority or organization issued this certificate?",
    "expiry_date": "What is the expiry date or valid-until date of this certificate?",
}


def clean(value: str) -> str:
    return re.sub(r"\s+", " ", value.strip(" :-.,;\t"))


def labeled_value(lines: list[str], label: str) -> str | None:
    marker = re.compile(rf"^(?:.*?\b)?(?:{label})\b\s*[:.\-]?\s*(.*)$", re.I)
    for index, line in enumerate(lines):
        match = marker.search(line)
        if not match:
            continue
        value = clean(match.group(1))
        if len(value) > 1:
            return value
        if index + 1 < len(lines):
            return clean(lines[index + 1])
    return None


def labeled_date(lines: list[str], label: str) -> str | None:
    marker = re.compile(label, re.I)
    for index, line in enumerate(lines):
        if marker.search(line):
            for candidate in (line, *lines[index + 1 : index + 2]):
                if match := DATE.search(candidate):
                    return clean(match.group())
    return None


def extract(lines: list[str]) -> dict[str, str]:
    text = "\n".join(lines)
    # The final standalone issuance line belongs to the certificate; earlier lines may
    # describe an attached declaration with a different issue date.
    issued_at = next(iter(reversed(list(re.finditer(
        rf"(?im)^issued\s+at\s+(.+?)\s+on\s+({DATE.pattern})", text
    )))), None)
    name = next(
        (
            line for line in lines[:60]
            if 12 <= len(line) <= 140 and line.lower() not in {"certificate", "attestation", "licence", "license"}
            and re.search(r"\b(certificate|attestation|licen[cs]e)\b", line, re.I)
            and not re.search(r"\b(no\.?|number|valid|issue|expiry|conditions|this certificate|annexure to)\b", line, re.I)
        ),
        None,
    )
    fields = {
        "certificate_name": name,
        "issue_date": clean(issued_at.group(2)) if issued_at else labeled_date(lines, r"date\s+(?:of\s+issue|issued)|issue\s+date|issued\s+on"),
        "issue_port": clean(issued_at.group(1)) if issued_at else labeled_value(lines, r"place\s+of\s+issue|port\s+of\s+issue"),
        "issuing_authority": next((clean(line[3:]) for line in lines[:40] if re.fullmatch(r"by\s+Indian Register of Shipping", line, re.I)), None)
            or labeled_value(lines, r"issuing\s+authority|issued\s+by"),
        "expiry_date": labeled_date(lines, r"expiry(?:\s+date|\s+dated)?|date\s+of\s+expiry|valid\s+(?:until|till)|expires\s+on"),
    }
    return {key: value for key, value in fields.items() if value and len(value) <= 160}


def prepare(manifest: Path, ocr_dir: Path, output: Path) -> None:
    splits: dict[str, list[dict]] = {"train": [], "val": [], "test": []}
    with manifest.open(encoding="utf-8-sig") as handle:
        rows = list(csv.DictReader(handle))
    for row in rows:
        source = row["source_pdf"]
        if row["duplicate_of"] or not INCLUDE.search(source) or EXCLUDE.search(source):
            continue
        path = ocr_dir / f"{row['id']}.json"
        if not path.exists():
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        lines = [clean(line) for line in data.get("rec_texts", []) if clean(line)]
        context = "\n".join(lines)
        for field, value in extract(lines).items():
            start = context.lower().find(value.lower())
            if start < 0:
                continue
            splits[row["split"]].append(
                {
                    "id": f"{row['id']}_{field}",
                    "source_pdf": source,
                    "field": field,
                    "question": QUESTIONS[field],
                    "context": context,
                    "answers": {"text": [context[start : start + len(value)]], "answer_start": [start]},
                }
            )
    output.mkdir(parents=True, exist_ok=True)
    for split, samples in splits.items():
        with (output / f"{split}.jsonl").open("w", encoding="utf-8") as handle:
            for sample in samples:
                handle.write(json.dumps(sample, ensure_ascii=False) + "\n")
        print(f"{split}: {len(samples)} QA samples")


def self_test() -> None:
    fields = extract([
        "CARGO SHIP CONSTRUCTION CERTIFICATE",
        "Issued by: Government of India",
        "This certificate is valid until 19/04/2027.",
        "Issued at Mumbai on 22/01/2026.",
    ])
    assert fields == {
        "certificate_name": "CARGO SHIP CONSTRUCTION CERTIFICATE",
        "issue_date": "22/01/2026",
        "issue_port": "Mumbai",
        "issuing_authority": "Government of India",
        "expiry_date": "19/04/2027",
    }
    assert extract([
        "MARITIME LABOUR CERTIFICATE",
        "The declaration was issued at Mumbai on 07/05/2025.",
        "Issued at MUMBAI on 19/05/2025 (dd/mm/yyyy).",
        "Date Issued", "29 Apr 2026",
    ])["issue_date"] == "19/05/2025"
    assert labeled_date(["Date Issued", "29 Apr 2026"], r"date\s+issued") == "29 Apr 2026"
    assert DATE.search("valid until 2028") is None
    print("self-test passed")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=Path("ml/data/processed/manifest.csv"))
    parser.add_argument("--ocr-dir", type=Path, default=Path("ml/data/bootstrap/ocr_json"))
    parser.add_argument("--output", type=Path, default=Path("ml/data/certificate_qa"))
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    self_test() if args.self_test else prepare(args.manifest, args.ocr_dir, args.output)
