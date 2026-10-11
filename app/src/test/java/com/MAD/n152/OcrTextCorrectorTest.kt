package com.MAD.n152

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextCorrectorTest {
    @Test
    fun corrects_close_document_words() {
        assertEquals("The document was scanned", OcrTextCorrector.correct("The documcnt was scanncd"))
    }

    @Test
    fun preserves_codes_and_unknown_words() {
        assertEquals("AB1234X zqxv", OcrTextCorrector.correct("AB1234X zqxv"))
    }

    @Test
    fun applies_digit_letter_confusions_inside_words() {
        assertEquals("The document was scanned", OcrTextCorrector.correct("The d0cument was 5canned"))
        assertEquals("Certificate of proficiency", OcrTextCorrector.correct("Certificate of pr0ficiency"))
    }

    @Test
    fun protects_identifiers_numbers_dates_and_ordinals() {
        val text = "No. NE-SCRB-2025-04817 COC12345 Cert1234 MV0123 m4ster 19/04/2027 18th June 2025 IMO 9876543"
        assertEquals(text, OcrTextCorrector.correct(text))
    }

    @Test
    fun keeps_maritime_vocabulary() {
        val text = "Minimum Safe Manning Certificate for the seafarer: proficiency, competency, endorsement, " +
            "mariner, vessel, tonnage, Port authority, Directorate of Shipping, maritime, watchkeeping, " +
            "survival, firefighting"
        assertEquals(text, OcrTextCorrector.correct(text))
    }

    @Test
    fun corrects_close_maritime_words() {
        assertEquals("Watchkeeping Seafarer", OcrTextCorrector.correct("Watchkeepinq Seafarcr"))
    }
}
