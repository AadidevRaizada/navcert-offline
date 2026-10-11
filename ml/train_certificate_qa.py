from __future__ import annotations

import argparse
import json
import random
from collections import Counter
from pathlib import Path

import torch
from torch.utils.data import DataLoader, Dataset, WeightedRandomSampler
from transformers import AutoModelForQuestionAnswering, AutoTokenizer


MODEL = "mightyneighbor/bert-tiny-finetuned-squad"


def load_jsonl(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


class CertificateQaDataset(Dataset):
    def __init__(self, rows: list[dict], tokenizer, max_length: int = 384):
        self.features = []
        self.fields = []
        for row in rows:
            answer = row["answers"]["text"][0]
            start = row["answers"]["answer_start"][0]
            end = start + len(answer)
            encoded = tokenizer(
                row["question"], row["context"], truncation="only_second", max_length=max_length,
                stride=96, return_offsets_mapping=True, return_overflowing_tokens=True, padding="max_length",
            )
            for index, offsets in enumerate(encoded.pop("offset_mapping")):
                sequence = encoded.sequence_ids(index)
                context_tokens = [i for i, value in enumerate(sequence) if value == 1]
                if not context_tokens or offsets[context_tokens[0]][0] > start or offsets[context_tokens[-1]][1] < end:
                    continue
                token_start = next(i for i in context_tokens if offsets[i][1] > start)
                token_end = next(i for i in reversed(context_tokens) if offsets[i][0] < end)
                feature = {
                    key: torch.tensor(encoded[key][index])
                    for key in ("input_ids", "attention_mask", "token_type_ids")
                    if key in encoded
                }
                feature["start_positions"] = torch.tensor(token_start)
                feature["end_positions"] = torch.tensor(token_end)
                self.features.append(feature)
                self.fields.append(row["field"])
                break

    def __len__(self):
        return len(self.features)

    def __getitem__(self, index):
        return self.features[index]


@torch.no_grad()
def exact_match(model, loader, device, fields: list[str]) -> tuple[float, float]:
    model.eval()
    correct = total = 0
    per_field: dict[str, list[int]] = {}
    for batch in loader:
        labels = (batch.pop("start_positions"), batch.pop("end_positions"))
        outputs = model(**{key: value.to(device) for key, value in batch.items()})
        matches = ((outputs.start_logits.argmax(-1).cpu() == labels[0]) &
                   (outputs.end_logits.argmax(-1).cpu() == labels[1])).tolist()
        for field, match in zip(fields[total : total + len(matches)], matches):
            stats = per_field.setdefault(field, [0, 0])
            stats[0] += int(match)
            stats[1] += 1
        correct += sum(matches)
        total += len(matches)
    macro = sum(right / count for right, count in per_field.values()) / max(len(per_field), 1)
    return correct / max(total, 1), macro


def train(data: Path, output: Path, epochs: int, base_model: str, balanced: bool) -> None:
    random.seed(42)
    torch.manual_seed(42)
    torch.set_num_threads(min(4, torch.get_num_threads()))
    tokenizer = AutoTokenizer.from_pretrained(base_model)
    model = AutoModelForQuestionAnswering.from_pretrained(base_model)
    train_set = CertificateQaDataset(load_jsonl(data / "train.jsonl"), tokenizer)
    val_set = CertificateQaDataset(load_jsonl(data / "val.jsonl"), tokenizer)
    if not train_set or not val_set:
        raise ValueError("Training and validation both need aligned QA spans")
    field_counts = Counter(train_set.fields)
    weights = [1.0 / field_counts[field] for field in train_set.fields]
    sampler = WeightedRandomSampler(weights, num_samples=len(train_set), replacement=True) if balanced else None
    train_loader = DataLoader(train_set, batch_size=8, sampler=sampler, shuffle=sampler is None)
    val_loader = DataLoader(val_set, batch_size=8)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model.to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=3e-5)
    best_score = -1.0
    best_epoch = 0
    stale_epochs = 0
    print(f"Training {len(train_set)} samples on {device} for {epochs} epochs")
    for epoch in range(epochs):
        model.train()
        loss_sum = 0.0
        for batch in train_loader:
            batch = {key: value.to(device) for key, value in batch.items()}
            loss = model(**batch).loss
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            optimizer.zero_grad(set_to_none=True)
            loss_sum += loss.item()
        overall, macro = exact_match(model, val_loader, device, val_set.fields)
        print(f"epoch {epoch + 1}: loss={loss_sum / max(len(train_loader), 1):.4f} val_exact={overall:.3f} val_macro={macro:.3f}", flush=True)
        if macro > best_score:
            best_score = macro
            best_epoch = epoch + 1
            stale_epochs = 0
            output.mkdir(parents=True, exist_ok=True)
            model.save_pretrained(output)
            tokenizer.save_pretrained(output)
        else:
            stale_epochs += 1
            if stale_epochs >= 5:
                print("Stopping after five validation epochs without improvement", flush=True)
                break
    (output / "training_summary.json").write_text(json.dumps({
        "base_model": base_model, "balanced_sampling": balanced, "train_samples": len(train_set), "validation_samples": len(val_set),
        "max_epochs": epochs, "best_epoch": best_epoch, "validation_macro_token_exact_match": best_score,
        "device": str(device), "weak_labels": True,
    }, indent=2), encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=Path, default=Path("ml/data/certificate_qa"))
    parser.add_argument("--output", type=Path, default=Path("ml/models/certificate-bert-tiny"))
    parser.add_argument("--epochs", type=int, default=8)
    parser.add_argument("--base-model", default=MODEL)
    parser.add_argument("--balanced", action="store_true")
    args = parser.parse_args()
    train(args.data, args.output, args.epochs, args.base_model, args.balanced)
