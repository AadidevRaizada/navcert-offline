"""Unit tests for the CSP consistency checker. Run: python -m unittest discover -s ml/ai_eval"""

import unittest

from csp_consistency import FIELDS, consistent, greedy_first, parse_date, solve


def base() -> dict:
    return {
        "certificate_name": ["International Load Line Certificate"],
        "issue_date": ["22/01/2026"],
        "issue_port": ["Mumbai"],
        "issuing_authority": ["Directorate General of Shipping"],
        "expiry_date": ["19/04/2027"],
    }


class CspConsistencyTest(unittest.TestCase):
    def test_parses_numeric_and_month_name_dates(self):
        self.assertEqual(parse_date("22/01/2026").isoformat(), "2026-01-22")
        self.assertEqual(parse_date("12 July 2025").isoformat(), "2025-07-12")
        self.assertIsNone(parse_date("19/05/202"))

    def test_clean_candidates_are_kept(self):
        result = solve(base())
        self.assertEqual(result.assignment, greedy_first(base()))
        self.assertTrue(consistent(result.assignment))

    def test_swapped_dates_are_repaired_by_expiry_after_issue(self):
        cands = base()
        cands["issue_date"].insert(0, "19/04/2027")
        cands["expiry_date"].insert(0, "22/01/2026")
        result = solve(cands)
        self.assertEqual(result.assignment["issue_date"], "22/01/2026")
        self.assertEqual(result.assignment["expiry_date"], "19/04/2027")
        self.assertFalse(consistent(greedy_first(cands)))

    def test_equipment_date_before_issue_is_rejected(self):
        cands = base()
        cands["expiry_date"].insert(0, "30/10/2025")  # e.g. a battery expiry printed higher up
        self.assertEqual(solve(cands).assignment["expiry_date"], "19/04/2027")

    def test_port_outside_gazetteer_is_pruned(self):
        cands = base()
        cands["issue_port"].insert(0, "A. Kumar")
        result = solve(cands)
        self.assertEqual(result.assignment["issue_port"], "Mumbai")
        self.assertGreaterEqual(result.stats.pruned_unary, 1)

    def test_abstains_instead_of_inventing_a_value(self):
        cands = base()
        cands["issuing_authority"] = ["Signature"]
        self.assertIsNone(solve(cands).assignment["issuing_authority"])

    def test_constraint_satisfying_error_is_not_detectable(self):
        cands = base()
        cands["expiry_date"].insert(0, "19/05/2027")  # wrong, but still after the issue date
        self.assertEqual(solve(cands).assignment["expiry_date"], "19/05/2027")

    def test_mrv_never_expands_more_nodes_than_static_order_here(self):
        cands = base()
        cands["issue_date"].insert(0, "19/04/2027")
        cands["expiry_date"].insert(0, "22/01/2026")
        mrv = solve(cands, mrv=True).stats.nodes
        static = solve(cands, mrv=False).stats.nodes
        self.assertLessEqual(mrv, static)

    def test_every_field_is_assigned(self):
        self.assertEqual(set(solve({}).assignment), set(FIELDS))


if __name__ == "__main__":
    unittest.main()
