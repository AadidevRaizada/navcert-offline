package com.MAD.n152

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.Closeable
import java.io.File
import java.nio.LongBuffer
import java.util.Locale

/** The certificate-only MiniLM checkpoint. OCR and saved-document Q&A remain separate. */
class CertificateOnnxAnswerer(private val context: Context) : Closeable {
    private val environment = OrtEnvironment.getEnvironment()
    private val lock = Any()
    private var session: OrtSession? = null
    private val tokenizer by lazy {
        CertificateWordPieceTokenizer(
            context.assets.open("nlp/vocab.txt").bufferedReader().useLines { it.toList() }
        )
    }

    fun answer(question: String, contextText: String): String? = synchronized(lock) {
        if (contextText.isBlank()) return@synchronized null
        val model = getSession()
        val context = contextText.take(MAX_CONTEXT_CHARS)
        val contextPieces = tokenizer.tokenize(context)
        val questionPieces = tokenizer.tokenize(question)
        val capacity = INPUT_LENGTH - questionPieces.size - 3
        if (capacity <= STRIDE) return@synchronized null
        var bestScore = Float.NEGATIVE_INFINITY
        var bestAnswer: String? = null
        var windowStart = 0
        while (windowStart < contextPieces.size) {
            val windowEnd = (windowStart + capacity).coerceAtMost(contextPieces.size)
            val ids = LongArray(INPUT_LENGTH)
            val mask = LongArray(INPUT_LENGTH)
            val types = LongArray(INPUT_LENGTH)
            val offsets = arrayOfNulls<IntRange>(INPUT_LENGTH)
            var position = 0
            fun put(id: Int, type: Long = 0, range: IntRange? = null) {
                ids[position] = id.toLong()
                mask[position] = 1
                types[position] = type
                offsets[position] = range
                position++
            }
            put(101) // [CLS]
            questionPieces.forEach { put(it.id) }
            put(102) // [SEP]
            val contextStart = position
            for (piece in contextPieces.subList(windowStart, windowEnd)) {
                put(piece.id, 1, piece.start until piece.end)
            }
            val contextEnd = position
            put(102, 1)

            val shape = longArrayOf(1, INPUT_LENGTH.toLong())
            OnnxTensor.createTensor(environment, LongBuffer.wrap(ids), shape).use { inputIds ->
                OnnxTensor.createTensor(environment, LongBuffer.wrap(mask), shape).use { attentionMask ->
                    OnnxTensor.createTensor(environment, LongBuffer.wrap(types), shape).use { tokenTypes ->
                        model.run(mapOf("input_ids" to inputIds, "attention_mask" to attentionMask,
                            "token_type_ids" to tokenTypes)).use { result ->
                            @Suppress("UNCHECKED_CAST")
                            val starts = (result[0].value as Array<FloatArray>)[0]
                            @Suppress("UNCHECKED_CAST")
                            val ends = (result[1].value as Array<FloatArray>)[0]
                            val topStarts = (contextStart until contextEnd)
                                .sortedByDescending { starts[it] }.take(12)
                            for (start in topStarts) {
                                val topEnds = (start until minOf(contextEnd, start + 32))
                                    .sortedByDescending { ends[it] }.take(12)
                                for (end in topEnds) {
                                    val first = offsets[start] ?: continue
                                    val last = offsets[end] ?: continue
                                    val candidate = context.substring(first.first, last.last + 1).trim()
                                    val score = starts[start] + ends[end]
                                    if (candidate.isNotEmpty() && candidate.length <= 200 && score > bestScore) {
                                        bestScore = score
                                        bestAnswer = candidate
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (windowEnd == contextPieces.size) break
            windowStart = windowEnd - STRIDE
        }
        bestAnswer
    }

    private fun getSession(): OrtSession {
        session?.let { return it }
        val folder = File(context.filesDir, "models/certificate").apply { mkdirs() }
        val target = File(folder, "certificate_minilm.int8.onnx")
        if (target.length() != MODEL_BYTES) {
            val temporary = File(folder, "certificate_minilm.int8.onnx.tmp")
            context.assets.open("nlp/certificate_minilm.int8.onnx").use { input ->
                temporary.outputStream().buffered().use(input::copyTo)
            }
            check(temporary.length() == MODEL_BYTES) { "Certificate model asset is incomplete" }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "Could not prepare certificate model" }
        }
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(2)
        }
        return options.use { environment.createSession(target.absolutePath, it).also { created -> session = created } }
    }

    override fun close() = synchronized(lock) {
        session?.close()
        session = null
    }

    private companion object {
        const val INPUT_LENGTH = 384
        const val STRIDE = 96
        const val MAX_CONTEXT_CHARS = 12_000 // ponytail: first-page focus; raise if long certificates need later pages.
        const val MODEL_BYTES = 33_820_437L
    }
}

internal class CertificateWordPieceTokenizer(vocabulary: List<String>) {
    private val ids = vocabulary.withIndex().associate { it.value to it.index }
    private val basic = Regex("[\\p{L}\\p{N}]+|[^\\s\\p{L}\\p{N}]")
    private val unknown = ids.getValue("[UNK]")

    data class Piece(val id: Int, val start: Int, val end: Int)

    fun tokenize(text: String): List<Piece> = buildList {
        for (match in basic.findAll(text)) {
            val value = match.value.lowercase(Locale.ROOT)
            if (value.length > 100) {
                add(Piece(unknown, match.range.first, match.range.last + 1))
                continue
            }
            val pieces = mutableListOf<Piece>()
            var cursor = 0
            while (cursor < value.length) {
                var end = value.length
                var found: Int? = null
                while (end > cursor) {
                    val key = (if (cursor == 0) "" else "##") + value.substring(cursor, end)
                    found = ids[key]
                    if (found != null) break
                    end--
                }
                if (found == null) break
                pieces += Piece(found, match.range.first + cursor, match.range.first + end)
                cursor = end
            }
            if (cursor == value.length) addAll(pieces)
            else add(Piece(unknown, match.range.first, match.range.last + 1))
        }
    }
}
