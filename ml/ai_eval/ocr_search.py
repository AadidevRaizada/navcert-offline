"""OCR post-correction as search: the app's greedy dictionary scan vs UCS and A*.

* ``app_correct`` is a line-by-line Python port of ``OcrTextCorrector.correctWord``
  (same dictionary, read from the Kotlin source; same guards, budget and tie rule).
* ``search_correct`` treats correction as a shortest-path problem.
  State  = (characters of the noisy word consumed, node in a dictionary trie).
  Goal   = whole noisy word consumed at a trie node that ends a word.
  Costs  = 0 match, 0.4 for a known OCR confusion (0/o, 1/l, 5/s, 6/g, 8/b, c/e, rn/m,
           cl/d, li/h, vv/w), 1 for any other substitution, insertion or deletion.
  UCS    = priority g(n).
  A*     = priority g(n) + h(n), h = 0.4 * |remaining noisy chars - nearest completion length|.
           Each unit of length mismatch needs at least one operation that changes length,
           and the cheapest such operation (a two-for-one confusion) costs 0.4, so h is
           admissible and consistent.
Noise is synthetic, generated from the dictionary itself. No certificate text is used.
"""

from __future__ import annotations

import heapq
import random
import re
from itertools import count
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KOTLIN = ROOT / "app/src/main/java/com/MAD/n152/OcrTextCorrector.kt"


def load_dictionary() -> list[str]:
    source = KOTLIN.read_text(encoding="utf-8")
    block = re.search(r'val dictionary = """(.*?)"""', source, re.S).group(1)
    return sorted({w.lower() for w in block.split()})


DICTIONARY = load_dictionary()
DICT_SET = set(DICTIONARY)
APP_CONFUSION = {"0": "o", "1": "l", "5": "s", "6": "g", "8": "b"}


def levenshtein(a: str, b: str) -> int:
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a):
        cur = [i + 1] + [0] * len(b)
        for j, cb in enumerate(b):
            cur[j + 1] = min(cur[j] + 1, prev[j + 1] + 1, prev[j] + (ca != cb))
        prev = cur
    return prev[-1]


def app_correct(original: str, digit_guard: bool = True) -> tuple[str, int]:
    """Port of OcrTextCorrector.correctWord. Returns (word, Levenshtein evaluations)."""
    lower = original.lower()
    if len(lower) < 4 or original.isupper() or (digit_guard and any(c.isdigit() for c in lower)):
        return original, 0
    if lower in DICT_SET:
        return original, 0
    normalized = "".join(APP_CONFUSION.get(c, c) for c in lower)
    budget = 2 if len(lower) >= 7 else 1
    scored = sorted(((w, levenshtein(normalized, w)) for w in DICTIONARY), key=lambda x: x[1])
    candidates = [x for x in scored if x[1] <= budget][:2]
    if not candidates:
        return original, len(DICTIONARY)
    second = candidates[1][1] if len(candidates) > 1 else 10**9
    if second - candidates[0][1] < 1:
        return original, len(DICTIONARY)
    best = candidates[0][0]
    return (best.capitalize() if original[0].isupper() else best), len(DICTIONARY)


# ------------------------------------------------------------------ trie search
class Trie:
    def __init__(self, words: list[str]):
        self.children: list[dict[str, int]] = [{}]
        self.terminal: list[str | None] = [None]
        for w in words:
            node = 0
            for ch in w:
                nxt = self.children[node].get(ch)
                if nxt is None:
                    nxt = len(self.children)
                    self.children[node][ch] = nxt
                    self.children.append({})
                    self.terminal.append(None)
                node = nxt
            self.terminal[node] = w
        # completion lengths reachable from each node (for the A* heuristic)
        self.depths: list[set[int]] = [set() for _ in self.children]
        for node in reversed(range(len(self.children))):
            if self.terminal[node]:
                self.depths[node].add(0)
            for child in self.children[node].values():
                self.depths[node].update(d + 1 for d in self.depths[child])


TRIE = Trie(DICTIONARY)
CONF_COST = 0.4
ONE_TO_ONE = {("0", "o"), ("1", "l"), ("5", "s"), ("6", "g"), ("8", "b"), ("c", "e"), ("e", "c"),
              ("1", "i"), ("l", "i"), ("i", "l"), ("u", "v"), ("v", "u")}
TWO_TO_ONE = {"rn": "m", "cl": "d", "li": "h", "vv": "w"}  # noisy pair -> true char
ONE_TO_TWO = {"m": "rn"}                                     # noisy char -> true pair


def heuristic(rem: int, node: int, astar: bool) -> float:
    if not astar:
        return 0.0
    return CONF_COST * min(abs(rem - d) for d in TRIE.depths[node])


def search(noisy: str, budget: float, astar: bool) -> tuple[list[tuple[str, float]], int]:
    """Best-first search; returns up to two distinct goal words with costs, and nodes expanded."""
    n = len(noisy)
    tie = count()
    start = (0, 0)
    frontier = [(heuristic(n, 0, astar), next(tie), 0.0, start)]
    best_g = {start: 0.0}
    closed: set = set()
    goals: list[tuple[str, float]] = []
    expanded = 0
    while frontier and len(goals) < 2:
        f, _, g, state = heapq.heappop(frontier)
        if state in closed or f > budget + 1e-9:
            if f > budget + 1e-9:
                break
            continue
        closed.add(state)
        expanded += 1
        i, node = state
        word = TRIE.terminal[node]
        if i == n and word and word not in (w for w, _ in goals):
            goals.append((word, round(g, 3)))
            continue

        def push(ni: int, nnode: int, step: float) -> None:
            ng = g + step
            key = (ni, nnode)
            if ng <= budget + 1e-9 and ng < best_g.get(key, float("inf")):
                best_g[key] = ng
                heapq.heappush(frontier, (ng + heuristic(n - ni, nnode, astar), next(tie), ng, key))

        kids = TRIE.children[node]
        if i < n:
            c = noisy[i]
            push(i + 1, node, 1.0)                                   # noise inserted a char
            for ch, child in kids.items():
                cost = 0.0 if ch == c else CONF_COST if (c, ch) in ONE_TO_ONE else 1.0
                push(i + 1, child, cost)
            pair = noisy[i:i + 2]
            if pair in TWO_TO_ONE and TWO_TO_ONE[pair] in kids:
                push(i + 2, kids[TWO_TO_ONE[pair]], CONF_COST)
            if c in ONE_TO_TWO:
                a, b = ONE_TO_TWO[c]
                mid = kids.get(a)
                if mid is not None and b in TRIE.children[mid]:
                    push(i + 1, TRIE.children[mid][b], CONF_COST)
        for child in kids.values():
            push(i, child, 1.0)                                      # noise dropped a char
    return goals, expanded


def search_correct(original: str, astar: bool) -> tuple[str, int]:
    lower = original.lower()
    digits = sum(ch.isdigit() for ch in lower)
    if len(lower) < 4 or original.isupper() or digits > 2 or lower in DICT_SET:
        return original, 0
    budget = 2.0 if len(lower) >= 7 else 1.0
    goals, expanded = search(lower, budget, astar)
    if not goals or (len(goals) > 1 and goals[1][1] - goals[0][1] < 0.3):
        return original, expanded
    best = goals[0][0]
    return (best.capitalize() if original[0].isupper() else best), expanded


# ------------------------------------------------------------------ synthetic noise
DIGIT_NOISE = {"o": "0", "l": "1", "s": "5", "g": "6", "b": "8"}
LETTER_NOISE = {"e": "c", "m": "rn", "d": "cl", "h": "li", "w": "vv", "i": "l"}


def corrupt(word: str, table: dict[str, str], rng: random.Random) -> str | None:
    spots = [i for i, ch in enumerate(word) if ch in table]
    if not spots:
        return None
    picks = rng.sample(spots, min(len(spots), 1 if len(word) < 7 else rng.choice([1, 2])))
    out = list(word)
    for i in picks:
        out[i] = table[word[i]]
    return "".join(out)


OOV = ["Mumbai", "Sharjah", "Kandla", "Mormugao", "Fujairah", "Piraeus", "Rotterdam", "Colombo",
       "Visakhapatnam", "Hamriyah", "Fernandes", "Mehta", "Kumar", "Lloyds", "Bureau", "Veritas",
       "Annexure", "tonnage", "manning", "sanitation", "pollution", "solas", "marpol", "gmdss",
       "vessel", "flag", "hull", "keel", "berth", "cargo", "tanker", "bulk", "radio", "survey"]


def run_experiment(seed: int = 152) -> dict:
    rng = random.Random(seed)
    sets: dict[str, list[tuple[str, str]]] = {"digit_confusions": [], "letter_confusions": []}
    for w in DICTIONARY:
        if len(w) < 4:
            continue
        for name, table in (("digit_confusions", DIGIT_NOISE), ("letter_confusions", LETTER_NOISE)):
            noisy = corrupt(w, table, rng)
            if noisy and noisy not in DICT_SET:
                sets[name].append((noisy, w))
    oov = [w for w in OOV if w.lower() not in DICT_SET]
    methods = {
        "app_greedy_scan": lambda w: app_correct(w),
        "app_scan_without_digit_guard": lambda w: app_correct(w, digit_guard=False),
        "uniform_cost_search": lambda w: search_correct(w, astar=False),
        "a_star_search": lambda w: search_correct(w, astar=True),
    }
    report: dict = {"dictionary_words": len(DICTIONARY), "trie_nodes": len(TRIE.children), "seed": seed}
    for set_name, pairs in sets.items():
        block = {"words": len(pairs)}
        for m, fn in methods.items():
            right = wrong = work = 0
            for noisy, truth in pairs:
                out, effort = fn(noisy)
                right += out == truth
                wrong += out != noisy and out != truth
                work += effort
            block[m] = {"recovered": round(right / len(pairs), 4), "miscorrected": round(wrong / len(pairs), 4),
                        "mean_work": round(work / len(pairs), 1)}
        report[set_name] = block
    block = {"words": len(oov)}
    for m, fn in methods.items():
        changed = [w for w in oov if fn(w)[0] != w]
        block[m] = {"changed": len(changed), "examples": [f"{w}->{fn(w)[0]}" for w in changed[:4]]}
    report["out_of_vocabulary_maritime_words"] = block
    report["work_unit"] = ("app: full Levenshtein evaluations against the dictionary; "
                           "UCS/A*: search nodes expanded")
    return report


if __name__ == "__main__":
    import json
    print(json.dumps(run_experiment(), indent=2))
