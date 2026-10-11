from __future__ import annotations

import argparse
from pathlib import Path

import onnx
import torch
from transformers import AutoModelForQuestionAnswering, AutoTokenizer


class QaOutputs(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, input_ids, attention_mask, token_type_ids):
        output = self.model(input_ids=input_ids, attention_mask=attention_mask, token_type_ids=token_type_ids)
        return output.start_logits, output.end_logits


def export(model_dir: Path, output: Path) -> None:
    tokenizer = AutoTokenizer.from_pretrained(model_dir)
    model = QaOutputs(AutoModelForQuestionAnswering.from_pretrained(model_dir)).eval()
    sample = tokenizer("What is the issue date?", "Date of Issue: 22/01/2026", return_tensors="pt")
    output.parent.mkdir(parents=True, exist_ok=True)
    torch.onnx.export(
        model,
        (sample["input_ids"], sample["attention_mask"], sample["token_type_ids"]),
        output,
        input_names=["input_ids", "attention_mask", "token_type_ids"],
        output_names=["start_logits", "end_logits"],
        dynamic_axes={name: {0: "batch", 1: "sequence"} for name in (
            "input_ids", "attention_mask", "token_type_ids", "start_logits", "end_logits"
        )},
        opset_version=17,
        dynamo=False,
    )
    onnx.checker.check_model(onnx.load(output))
    print(f"Exported {output} ({output.stat().st_size / 1_000_000:.1f} MB)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", type=Path, default=Path("ml/models/certificate-bert-tiny"))
    parser.add_argument("--output", type=Path, default=Path("ml/models/certificate-bert-tiny/model.onnx"))
    args = parser.parse_args()
    export(args.model, args.output)
