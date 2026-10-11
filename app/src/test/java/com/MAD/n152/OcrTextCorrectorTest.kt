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
}
