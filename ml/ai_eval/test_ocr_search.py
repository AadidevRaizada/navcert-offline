"""Parity and behaviour tests for the OCR-correction search demo."""

import unittest

from ocr_search import app_correct, search_correct


class OcrSearchTest(unittest.TestCase):
    def test_python_port_matches_kotlin_unit_test(self):
        # Same cases as app/src/test/.../OcrTextCorrectorTest.kt
        words = "The documcnt was scanncd".split()
        self.assertEqual(" ".join(app_correct(w)[0] for w in words), "The document was scanned")
        self.assertEqual(app_correct("AB1234X")[0], "AB1234X")
        self.assertEqual(app_correct("zqxv")[0], "zqxv")

    def test_app_digit_guard_blocks_digit_confusions(self):
        self.assertEqual(app_correct("d0cument")[0], "d0cument")

    def test_search_repairs_digit_and_split_glyph_confusions(self):
        for astar in (False, True):
            self.assertEqual(search_correct("d0cument", astar)[0], "document")
            self.assertEqual(search_correct("inforrnation", astar)[0], "inforrnation")  # not in dictionary
            self.assertEqual(search_correct("forrnat", astar)[0], "forrnat")
            self.assertEqual(search_correct("prornpt", astar)[0], "prornpt")
            self.assertEqual(search_correct("narne", astar)[0], "name")

    def test_a_star_expands_no_more_nodes_than_ucs(self):
        for word in ("d0cument", "narne", "irnage", "5canning"):
            self.assertEqual(search_correct(word, True)[0], search_correct(word, False)[0])
            self.assertLessEqual(search_correct(word, True)[1], search_correct(word, False)[1])

    def test_codes_are_protected(self):
        self.assertEqual(search_correct("AB1234X", True)[0], "AB1234X")


if __name__ == "__main__":
    unittest.main()
