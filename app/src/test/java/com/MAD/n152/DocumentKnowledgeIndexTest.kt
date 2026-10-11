package com.MAD.n152

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentKnowledgeIndexTest {
    @Test
    fun retrievesRelevantDocumentChunk() {
        val documents = listOf(
            document("one", "Physics", "Newton's second law states that force equals mass times acceleration."),
            document("two", "Biology", "Photosynthesis converts light energy into chemical energy.")
        )

        val chunks = DocumentKnowledgeIndex.build(documents)
        val result = DocumentKnowledgeIndex.retrieve("What does photosynthesis convert?", chunks)

        assertEquals("two", result.first().documentId)
        assertTrue(result.first().text.contains("chemical energy"))
    }

    private fun document(id: String, title: String, text: String) = StoredDocument(
        id, title, text, 1L, 1L, text.split(' ').size, 1, null
    )
}
