package com.MAD.n152

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class CertificateWordPieceTokenizerTest {
    @Test
    fun matchesTrainingTokenizerOnCertificateText() {
        val vocabulary = File("src/main/assets/nlp/vocab.txt").readLines()
        val tokenizer = CertificateWordPieceTokenizer(vocabulary)
        val question = "At which port or place was this certificate issued?"
        val context = "Issued at Mumbai on 19/05/2025."
        assertEquals(
            listOf(2012, 2029, 3417, 2030, 2173, 2001, 2023, 8196, 3843, 1029),
            tokenizer.tokenize(question).map { it.id }
        )
        assertEquals(
            listOf(3843, 2012, 8955, 2006, 2539, 1013, 5709, 1013, 16798, 2629, 1012),
            tokenizer.tokenize(context).map { it.id }
        )
        val date = tokenizer.tokenize(context).filter { it.start >= 20 && it.end <= 30 }
        assertEquals("19/05/2025", context.substring(date.first().start, date.last().end))
    }
}
