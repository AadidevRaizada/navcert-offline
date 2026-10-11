"""Evaluate extractive QA against held-out, human-checked certificate fields."""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

import torch
from transformers import AutoModelForQuestionAnswering, AutoTokenizer


def normalize(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip(" \t\r\n.,:;").casefold()


@torch.no_grad()
def predict(model, tokenizer, question: str, context: str) -> str:
    encoded = tokenizer(
        question, context, truncation="only_second", max_length=384, stride=96,
        return_overflowing_tokens=True, return_offsets_mapping=True, padding="max_length",
    )
    offsets = encoded.pop("offset_mapping")
    encoded.pop("overflow_to_sample_mapping", None)
    best_score = float("-inf")
    best_text = ""
    for window, mapping in enumerate(offsets):
        sequence = encoded.sequence_ids(window)
        inputs = {key: torch.tensor([encoded[key][window]]) for key in ("input_ids", "attention_mask", "token_type_ids") if key in encoded}
        if hasattr(model, "run"):
            logits = model.run(None, {key: value.numpy() for key, value in inputs.items()})
            starts, ends = (torch.from_numpy(value[0]) for value in logits)
        else:
            output = model(**inputs)
            starts, ends = output.start_logits[0], output.end_logits[0]
        context_tokens = [index for index, segment in enumerate(sequence) if segment == 1 and mapping[index][1] > mapping[index][0]]
        for start in sorted(context_tokens, key=lambda i: starts[i].item(), reverse=True)[:12]:
            for end in sorted((i for i in context_tokens if start <= i < start + 32), key=lambda i: ends[i].item(), reverse=True)[:12]:
                candidate = context[mapping[start][0] : mapping[end][1]].strip()
                score = (starts[start] + ends[end]).item()
                if candidate and len(candidate) <= 200 and score > best_score:
                    best_score, best_text = score, candidate
    return best_text


def evaluate(model_dir: str | Path, data: Path, output: Path | None, onnx: Path | None = None) -> dict:
    torch.set_num_threads(min(4, torch.get_num_threads()))
    tokenizer = AutoTokenizer.from_pretrained(str(model_dir))
    if onnx:
        import onnxruntime as ort
        model = ort.InferenceSession(str(onnx), providers=["CPUExecutionProvider"])
    else:
        model = AutoModelForQuestionAnswering.from_pretrained(str(model_dir)).eval()
    rows = [json.loads(line) for line in data.read_text(encoding="utf-8").splitlines() if line]
    if not rows:
        raise ValueError("Empty evaluation set")
    by_field = defaultdict(lambda: Counter())
    predictions = []
    for row in rows:
        expected = row["answers"]["text"][0]
        predicted = predict(model, tokenizer, row["question"], row["context"])
        correct = normalize(expected) == normalize(predicted)
        by_field[row["field"]]["total"] += 1
        by_field[row["field"]]["correct"] += correct
        predictions.append({"id": row["id"], "field": row["field"], "source_pdf": row["source_pdf"],
                            "expected": expected, "predicted": predicted, "correct": correct})
    correct = sum(row["correct"] for row in predictions)
    summary = {"model": str(onnx or model_dir), "dataset": str(data), "exact_match": correct / len(rows),
               "correct": correct, "total": len(rows), "by_field": dict(by_field), "predictions": predictions}
    if output:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Exact answer match: {correct}/{len(rows)} = {correct / len(rows):.1%}")
    for field, metric in by_field.items():
        print(f"{field}: {metric['correct']}/{metric['total']}")
    return summary


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="ml/models/certificate-bert-tiny")
    parser.add_argument("--data", type=Path, default=Path("ml/data/certificate_qa_augmented/gold_test.jsonl"))
    parser.add_argument("--output", type=Path)
    parser.add_argument("--onnx", type=Path)
    args = parser.parse_args()
    evaluate(args.model, args.data, args.output, args.onnx)
