"""Certificate-field consistency as a Constraint Satisfaction Problem (CSP).

Variables : the five certificate fields.
Domains   : candidate spans proposed by a rule extractor, in rule-priority order,
            plus ``None`` (abstain) as the last value of every domain.
Constraints
  unary   : dates must parse; port must be in a port gazetteer; authority must look
            like an organisation; certificate name must carry a certificate cue.
  binary  : expiry > issue; expiry - issue <= MAX_VALIDITY_DAYS; name != authority.
Solver    : node consistency (unary pruning) -> backtracking search with
            MRV (minimum remaining values) variable ordering, forward checking and a
            branch-and-bound preference for filling more fields with higher-priority
            candidates.

All data here is synthetic. Nothing is read from the private certificate corpus.
"""

from __future__ import annotations

import random
import re
from dataclasses import dataclass, field
from datetime import date, timedelta

FIELDS = ["certificate_name", "issue_date", "issue_port", "issuing_authority", "expiry_date"]
MAX_VALIDITY_DAYS = 10 * 366
ABSTAIN_COST = 100  # loose plausibility bound chosen for this demo, not a regulation

PORT_GAZETTEER = {
    "mumbai", "chennai", "kolkata", "kochi", "kandla", "visakhapatnam", "mormugao", "paradip",
    "sharjah", "dubai", "fujairah", "singapore", "rotterdam", "hamburg", "piraeus", "busan",
    "colombo", "port klang", "jebel ali", "hamriyah port",
}
MONTHS = {m: i + 1 for i, m in enumerate(
    ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"])}
CERT_CUE = re.compile(r"\b(certificate|attestation|licen[cs]e|document of compliance)\b", re.I)
ORG_CUE = re.compile(r"\b(government|authority|register|registry|society|bureau|maritime|shipping|administration|ltd|limited)\b", re.I)


def parse_date(text: str) -> date | None:
    """Parses dd/mm/yyyy, dd-mm-yyyy, dd.mm.yyyy and '12 July 2025' style dates."""
    text = text.strip()
    m = re.fullmatch(r"(\d{1,2})[./-](\d{1,2})[./-](\d{4})", text)
    try:
        if m:
            return date(int(m[3]), int(m[2]), int(m[1]))
        m = re.fullmatch(r"(\d{1,2})(?:st|nd|rd|th)?[\s-]*([A-Za-z]{3,9})[\s,-]+(\d{4})", text)
        if m and m[2][:3].lower() in MONTHS:
            return date(int(m[3]), MONTHS[m[2][:3].lower()], int(m[1]))
    except ValueError:
        return None
    return None


def in_gazetteer(text: str) -> bool:
    first = text.split(",")[0].strip().lower()
    return first in PORT_GAZETTEER


UNARY = {
    "certificate_name": lambda v: bool(CERT_CUE.search(v)) and not re.match(r"(?i)(expiry|valid|date)", v),
    "issue_date": lambda v: parse_date(v) is not None,
    "expiry_date": lambda v: parse_date(v) is not None,
    "issue_port": in_gazetteer,
    "issuing_authority": lambda v: bool(ORG_CUE.search(v)) and len(v) <= 100,
}


def binary_ok(a: str, va, b: str, vb) -> bool:
    """True if the pair (a=va, b=vb) satisfies every binary constraint. None always passes."""
    if va is None or vb is None:
        return True
    pair = {a: va, b: vb}
    if {"issue_date", "expiry_date"} <= pair.keys():
        issued, expires = parse_date(pair["issue_date"]), parse_date(pair["expiry_date"])
        return expires > issued and (expires - issued).days <= MAX_VALIDITY_DAYS
    if {"certificate_name", "issuing_authority"} <= pair.keys():
        return pair["certificate_name"].casefold() != pair["issuing_authority"].casefold()
    return True


@dataclass
class Stats:
    nodes: int = 0          # partial assignments generated (search-tree nodes)
    backtracks: int = 0
    pruned_unary: int = 0   # candidate values removed by node consistency


@dataclass
class Result:
    assignment: dict
    stats: Stats = field(default_factory=Stats)


def node_consistent_domains(candidates: dict[str, list[str]], stats: Stats) -> dict[str, list]:
    domains = {}
    for name in FIELDS:
        values = candidates.get(name, [])
        kept = [v for v in values if UNARY[name](v)]
        stats.pruned_unary += len(values) - len(kept)
        domains[name] = kept + [None]
    return domains


def solve(candidates: dict[str, list[str]], mrv: bool = True, forward_check: bool = True) -> Result:
    """Backtracking with branch-and-bound over the hard constraints.

    Among all consistent assignments it returns the one that abstains on the fewest
    fields, breaking ties by rule priority (lower candidate rank wins). Abstaining
    costs ABSTAIN_COST; choosing the k-th surviving candidate costs k.
    """
    stats = Stats()
    domains = node_consistent_domains(candidates, stats)
    best: dict = {"cost": float("inf"), "assignment": None}

    def cost_of(var: str, value) -> int:
        return ABSTAIN_COST if value is None else domains[var].index(value)

    def select(assignment: dict, live: dict) -> str:
        unassigned = [f for f in FIELDS if f not in assignment]
        if not mrv:
            return unassigned[0]
        return min(unassigned, key=lambda f: (len(live[f]), FIELDS.index(f)))

    def backtrack(assignment: dict, live: dict, cost: int) -> None:
        if cost >= best["cost"]:          # bound: cannot beat the incumbent
            return
        if len(assignment) == len(FIELDS):
            best["cost"], best["assignment"] = cost, dict(assignment)
            return
        var = select(assignment, live)
        for value in live[var]:
            stats.nodes += 1
            if not all(binary_ok(var, value, other, assignment[other]) for other in assignment):
                stats.backtracks += 1
                continue
            assignment[var] = value
            next_live = live
            if forward_check:   # prune neighbours' domains; None is always kept so none wipes out
                next_live = {
                    f: ([v for v in vals if binary_ok(var, value, f, v)] if f not in assignment else vals)
                    for f, vals in live.items()
                }
            backtrack(assignment, next_live, cost + cost_of(var, value))
            del assignment[var]

    backtrack({}, domains, 0)
    return Result(best["assignment"] or {f: None for f in FIELDS}, stats)


def greedy_first(candidates: dict[str, list[str]]) -> dict:
    """Baseline that mirrors first-match rule priority: take each field's top candidate."""
    return {f: (candidates.get(f) or [None])[0] for f in FIELDS}


def consistent(assignment: dict) -> bool:
    if any(v is not None and not UNARY[f](v) for f, v in assignment.items()):
        return False
    return all(binary_ok(a, assignment[a], b, assignment[b])
               for i, a in enumerate(FIELDS) for b in FIELDS[i + 1:])


# ---------------------------------------------------------------- synthetic data
TITLES = [
    "Cargo Ship Safety Equipment Certificate", "International Load Line Certificate",
    "International Oil Pollution Prevention Certificate", "Safety Management Certificate",
    "Document of Compliance", "International Tonnage Certificate", "Minimum Safe Manning Certificate",
    "Certificate of Registry", "Ship Sanitation Control Exemption Certificate",
]
AUTHORITIES = [
    "Government of India", "Directorate General of Shipping", "Indian Register of Shipping",
    "Maritime Administration of Panama", "Classification Society Bureau", "Port State Registry",
]
PEOPLE = ["A. Kumar", "R. Mehta", "J. Fernandes", "S. Rao"]
NOISE_LINES = ["Signature", "Stamp", "Page 1 of 2", "Annexure"]


def fmt(d: date, style: int) -> str:
    return d.strftime("%d/%m/%Y") if style == 0 else f"{d.day} {d.strftime('%B')} {d.year}"


def synthetic_case(rng: random.Random) -> tuple[dict, dict, str]:
    """Returns (truth, rule candidates, distractor kind)."""
    issued = date(2020, 1, 1) + timedelta(days=rng.randrange(0, 5 * 365))
    expires = issued + timedelta(days=rng.choice([365, 2 * 365, 5 * 365 - 1]))
    style = rng.randrange(2)
    truth = {
        "certificate_name": rng.choice(TITLES),
        "issue_date": fmt(issued, style),
        "issue_port": rng.choice(sorted(PORT_GAZETTEER)).title(),
        "issuing_authority": rng.choice(AUTHORITIES),
        "expiry_date": fmt(expires, style),
    }
    cands = {f: [v] for f, v in truth.items()}
    kind = rng.choice(["clean", "equipment_date", "swapped_dates", "person_as_port",
                       "noise_authority", "consistent_wrong_date", "truncated_date"])
    if kind == "equipment_date":      # e.g. a battery expiry printed above the real expiry
        cands["expiry_date"].insert(0, fmt(issued - timedelta(days=rng.randrange(30, 400)), style))
    elif kind == "swapped_dates":     # issue/expiry labels read in the wrong order
        cands["issue_date"].insert(0, truth["expiry_date"])
        cands["expiry_date"].insert(0, truth["issue_date"])
    elif kind == "person_as_port":    # the line under "Place of issue" is an engineer's name
        cands["issue_port"].insert(0, rng.choice(PEOPLE))
    elif kind == "noise_authority":
        cands["issuing_authority"].insert(0, rng.choice(NOISE_LINES))
    elif kind == "consistent_wrong_date":  # wrong but constraint-satisfying: CSP cannot catch it
        cands["expiry_date"].insert(0, fmt(expires + timedelta(days=rng.randrange(1, 300)), style))
    elif kind == "truncated_date":    # OCR cut the year, e.g. "19/05/202"
        cands["issue_date"].insert(0, fmt(issued, 0)[:-1])
    return truth, cands, kind


def run_experiment(n: int = 2000, seed: int = 152) -> dict:
    rng = random.Random(seed)
    totals = {"greedy": [0, 0, 0], "csp": [0, 0, 0]}  # field hits, record hits, consistent records
    by_kind: dict[str, dict[str, int]] = {}
    search = {"mrv_fc": [0, 0], "static_fc": [0, 0], "static_plain": [0, 0]}  # nodes, backtracks
    pruned = 0
    for _ in range(n):
        truth, cands, kind = synthetic_case(rng)
        g = greedy_first(cands)
        r = solve(cands)
        pruned += r.stats.pruned_unary
        for name, (mrv, fc) in {"mrv_fc": (True, True), "static_fc": (False, True), "static_plain": (False, False)}.items():
            s = solve(cands, mrv=mrv, forward_check=fc).stats
            search[name][0] += s.nodes
            search[name][1] += s.backtracks
        k = by_kind.setdefault(kind, {"cases": 0, "greedy_record": 0, "csp_record": 0})
        k["cases"] += 1
        for label, a in (("greedy", g), ("csp", r.assignment)):
            hits = sum(a[f] == truth[f] for f in FIELDS)
            totals[label][0] += hits
            totals[label][1] += hits == len(FIELDS)
            totals[label][2] += consistent(a)
            k[f"{label}_record"] += hits == len(FIELDS)
    return {
        "synthetic_cases": n,
        "seed": seed,
        "field_accuracy": {m: round(v[0] / (n * len(FIELDS)), 4) for m, v in totals.items()},
        "record_exact": {m: round(v[1] / n, 4) for m, v in totals.items()},
        "records_satisfying_all_constraints": {m: round(v[2] / n, 4) for m, v in totals.items()},
        "by_distractor_kind": by_kind,
        "mean_search_nodes": {m: round(v[0] / n, 3) for m, v in search.items()},
        "mean_backtracks": {m: round(v[1] / n, 3) for m, v in search.items()},
        "mean_values_pruned_by_node_consistency": round(pruned / n, 3),
    }


if __name__ == "__main__":
    import json
    print(json.dumps(run_experiment(), indent=2))
