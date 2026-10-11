package com.MAD.n152

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentFormattingTest {
    @Test
    fun formatsOnlySelectedLinesAndReplacesExistingListMarkers() {
        val source = TextFieldValue("Title\nfirst\n• second\nEnd", TextRange(6, 20))

        val result = formatSelectedLines(source, numbered = true)

        assertEquals("Title\n1. first\n2. second\nEnd", result.text)
    }
}
