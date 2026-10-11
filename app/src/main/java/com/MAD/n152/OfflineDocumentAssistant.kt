package com.MAD.n152

import android.content.Context
import org.tensorflow.lite.task.text.qa.BertQuestionAnswerer
import java.io.Closeable
import kotlin.math.ln

data class DocumentChunk(
    val documentId: String,
    val documentTitle: String,
    val folderId: String?,
    val text: String
)

data class DocumentAnswer(
    val text: String,
    val documentId: String? = null,
    val documentTitle: String? = null,
    val excerpt: String? = null,
    val confidence: Float? = null
)

object DocumentKnowledgeIndex {
    private const val CHUNK_SIZE = 1200
    private const val CHUNK_OVERLAP = 180
    private val termPattern = Regex("[\\p{L}\\p{N}']+")
    private val stopWords = setOf(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "how", "in",
        "is", "it", "of", "on", "or", "that", "the", "this", "to", "was", "what",
        "when", "where", "which", "who", "why", "with"
    )

    fun build(documents: List<StoredDocument>, folderId: String? = null): List<DocumentChunk> =
        documents.asSequence()
            .filter { folderId == null || it.folderId == folderId }
            .flatMap { document -> chunk(document).asSequence() }
            .toList()

    fun retrieve(question: String, chunks: List<DocumentChunk>, limit: Int = 4): List<DocumentChunk> {
        val terms = terms(question)
        if (terms.isEmpty() || chunks.isEmpty()) return emptyList()
        val frequencies = terms.associateWith { term ->
            chunks.count { chunk -> term in terms(chunk.text + " " + chunk.documentTitle) }
        }
        return chunks.mapNotNull { chunk ->
            val chunkTerms = terms(chunk.text)
            val titleTerms = terms(chunk.documentTitle)
            val score = terms.sumOf { term ->
                val occurrences = chunkTerms.count { it == term } + titleTerms.count { it == term } * 3
                if (occurrences == 0) 0.0
                else occurrences * (ln((chunks.size + 1.0) / (frequencies.getValue(term) + 1.0)) + 1.0)
            }
            chunk.takeIf { score > 0.0 }?.let { it to score }
        }.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    private fun chunk(document: StoredDocument): List<DocumentChunk> {
        val text = document.text.replace(Regex("\\s+"), " ").trim()
        if (text.isBlank()) return emptyList()
        val chunks = mutableListOf<DocumentChunk>()
        var start = 0
        while (start < text.length) {
            var end = (start + CHUNK_SIZE).coerceAtMost(text.length)
            if (end < text.length) {
                val boundary = text.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'), end)
                if (boundary > start + CHUNK_SIZE / 2) end = boundary + 1
            }
            chunks += DocumentChunk(document.id, document.title, document.folderId, text.substring(start, end).trim())
            if (end == text.length) break
            start = (end - CHUNK_OVERLAP).coerceAtLeast(start + 1)
        }
        return chunks
    }

    private fun terms(text: String): List<String> = termPattern.findAll(text.lowercase())
        .map(MatchResult::value)
        .filter { it.length > 1 && it !in stopWords }
        .toList()
}

class OfflineDocumentAssistant(context: Context) : Closeable {
    private val certificateAnswerer = CertificateOnnxAnswerer(context.applicationContext)
    private val answererLazy = lazy {
        BertQuestionAnswerer.createBertQuestionAnswererFromFile(
            context.applicationContext,
            "nlp/mobilebert_qa.tflite",
            "nlp/vocab.txt"
        )
    }
    private val answerer by answererLazy

    fun answer(question: String, documents: List<StoredDocument>, folderId: String? = null): DocumentAnswer {
        val candidates = DocumentKnowledgeIndex.retrieve(
            question,
            DocumentKnowledgeIndex.build(documents, folderId)
        )
        if (candidates.isEmpty()) return DocumentAnswer("I couldn't find that in the selected documents.")

        val best = candidates.flatMap { chunk ->
            answerer.answer(chunk.text, question).take(1).map { answer -> Triple(chunk, answer.text.trim(), answer.pos.logit) }
        }.filter { it.second.isNotBlank() }.maxByOrNull { it.third }
            ?: return DocumentAnswer("I couldn't find a clear answer in the selected documents.")

        return DocumentAnswer(
            text = best.second,
            documentId = best.first.documentId,
            documentTitle = best.first.documentTitle,
            excerpt = best.first.text,
            confidence = best.third
        )
    }

    fun extractCertificateFields(text: String, fallbackTitle: String): CertificateFields =
        CertificateFieldExtractor.extract(text, fallbackTitle) { question ->
            runCatching { certificateAnswerer.answer(question, text) }.getOrNull()
                ?: answerer.answer(text, question).firstOrNull()?.text?.trim()
        }

    override fun close() {
        certificateAnswerer.close()
        if (answererLazy.isInitialized()) answerer.close()
    }
}
