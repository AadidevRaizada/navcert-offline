package com.MAD.n152

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class HandwritingResult(
    val text: String,
    val lineCount: Int,
    val averageConfidence: Float
)

/**
 * Offline PP-OCRv5 English line recognizer.
 *
 * ML Kit supplies document layout and line boxes. PP-OCRv5 re-recognizes each crop because its
 * mobile English model is explicitly trained for handwritten English. The ONNX session is loaded
 * lazily, so printed scans do not pay its startup or memory cost.
 */
class PaddleHandwritingRecognizer(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val sessionLock = Any()

    @Volatile
    private var session: OrtSession? = null

    @Volatile
    private var vocabulary: Array<String>? = null

    fun recognize(bitmap: Bitmap, layout: Text): HandwritingResult {
        val recognizer = getSession()
        val vocab = getVocabulary()
        val blockResults = mutableListOf<String>()
        val confidences = mutableListOf<Float>()
        var recognizedLines = 0

        layout.textBlocks.forEach { block ->
            val lines = block.lines.mapNotNull { line ->
                val bounds = line.boundingBox ?: return@mapNotNull null
                val crop = cropLine(bitmap, bounds) ?: return@mapNotNull null
                try {
                    val output = recognizeLine(recognizer, vocab, crop)
                    val paddleText = output.text.trim()
                    val chosen = when {
                        paddleText.isNotBlank() && output.confidence >= MIN_CONFIDENCE -> paddleText
                        line.text.isNotBlank() -> line.text.trim()
                        else -> paddleText
                    }
                    if (chosen.isBlank()) return@mapNotNull null
                    recognizedLines++
                    if (output.confidence > 0f) confidences += output.confidence
                    chosen
                } finally {
                    crop.recycle()
                }
            }
            if (lines.isNotEmpty()) blockResults += lines.joinToString("\n")
        }

        // A detector can occasionally return no lines for very loose handwriting. In that case,
        // keep ML Kit's baseline instead of returning an empty document.
        val text = blockResults.joinToString("\n\n").ifBlank { layout.text.trim() }
        return HandwritingResult(
            text = text,
            lineCount = if (recognizedLines > 0) recognizedLines else text.lineSequence().count(),
            averageConfidence = if (confidences.isEmpty()) 0f else confidences.average().toFloat()
        )
    }

    private fun getSession(): OrtSession {
        session?.let { return it }
        synchronized(sessionLock) {
            session?.let { return it }
            val model = materializeAsset(MODEL_ASSET, MODEL_FILE)
            val options = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setIntraOpNumThreads(max(2, Runtime.getRuntime().availableProcessors() / 2))
            }
            return options.use {
                environment.createSession(model.absolutePath, it).also { created -> session = created }
            }
        }
    }

    private fun getVocabulary(): Array<String> {
        vocabulary?.let { return it }
        synchronized(sessionLock) {
            vocabulary?.let { return it }
            val tokens = mutableListOf("") // CTC blank class at index zero.
            appContext.assets.open(DICT_ASSET).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    // Do not trim. The final dictionary entry is the actual space token.
                    if (line.isNotEmpty()) tokens += line
                }
            }
            return tokens.toTypedArray().also { vocabulary = it }
        }
    }

    private fun materializeAsset(assetPath: String, fileName: String): File {
        val modelDirectory = File(appContext.filesDir, "models/handwriting").apply { mkdirs() }
        val target = File(modelDirectory, fileName)
        if (target.exists() && target.length() == EXPECTED_MODEL_BYTES) return target
        val temporary = File(modelDirectory, "$fileName.tmp")
        appContext.assets.open(assetPath).use { input ->
            temporary.outputStream().buffered().use(input::copyTo)
        }
        check(temporary.length() == EXPECTED_MODEL_BYTES) { "Handwriting model is incomplete" }
        if (target.exists()) target.delete()
        check(temporary.renameTo(target)) { "Could not prepare handwriting model" }
        return target
    }

    private fun cropLine(bitmap: Bitmap, source: Rect): Bitmap? {
        val horizontalMargin = max(4, (source.width() * 0.04f).roundToInt())
        val verticalMargin = max(4, (source.height() * 0.22f).roundToInt())
        val left = max(0, source.left - horizontalMargin)
        val top = max(0, source.top - verticalMargin)
        val right = min(bitmap.width, source.right + horizontalMargin)
        val bottom = min(bitmap.height, source.bottom + verticalMargin)
        if (right - left < 3 || bottom - top < 3) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun recognizeLine(
        ortSession: OrtSession,
        vocab: Array<String>,
        source: Bitmap
    ): LineOutput {
        val sourceWidth = source.width
        val sourceHeight = max(1, source.height)
        val scaledWidth = max(MIN_WIDTH, (sourceWidth * INPUT_HEIGHT.toDouble() / sourceHeight).roundToInt())
            .coerceAtMost(MAX_WIDTH)
        val paddedWidth = ((scaledWidth + WIDTH_STRIDE - 1) / WIDTH_STRIDE) * WIDTH_STRIDE
        val prepared = Bitmap.createBitmap(paddedWidth, INPUT_HEIGHT, Bitmap.Config.ARGB_8888)

        try {
            val canvas = Canvas(prepared)
            canvas.drawColor(Color.WHITE)
            val matrix = Matrix().apply {
                postScale(
                    scaledWidth.toFloat() / source.width,
                    INPUT_HEIGHT.toFloat() / source.height
                )
            }
            canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            val input = bitmapToNchw(prepared)
            val shape = longArrayOf(1, 3, INPUT_HEIGHT.toLong(), paddedWidth.toLong())
            val inputName = ortSession.inputNames.first()

            synchronized(sessionLock) {
                OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), shape).use { tensor ->
                    ortSession.run(mapOf(inputName to tensor)).use { result ->
                        @Suppress("UNCHECKED_CAST")
                        val output = result[0].value as Array<Array<FloatArray>>
                        val logits = output.firstOrNull() ?: return LineOutput("", 0f)
                        check(logits.firstOrNull()?.size == vocab.size) {
                            "Handwriting vocabulary does not match the model"
                        }
                        return decodeCtc(logits, vocab)
                    }
                }
            }
        } finally {
            prepared.recycle()
        }
    }

    private fun bitmapToNchw(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val planeSize = width * height
        val pixels = IntArray(planeSize)
        val output = FloatArray(planeSize * 3)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        pixels.forEachIndexed { index, pixel ->
            output[index] = (Color.red(pixel) / 127.5f) - 1f
            output[planeSize + index] = (Color.green(pixel) / 127.5f) - 1f
            output[planeSize * 2 + index] = (Color.blue(pixel) / 127.5f) - 1f
        }
        return output
    }

    private fun decodeCtc(logits: Array<FloatArray>, vocab: Array<String>): LineOutput {
        val result = StringBuilder()
        var previous = -1
        var confidenceTotal = 0.0
        var tokenCount = 0

        logits.forEach { row ->
            if (row.isEmpty()) return@forEach
            var bestIndex = 0
            var bestValue = row[0]
            for (index in 1 until row.size) {
                if (row[index] > bestValue) {
                    bestValue = row[index]
                    bestIndex = index
                }
            }
            if (bestIndex != 0 && bestIndex != previous) {
                result.append(vocab.getOrElse(bestIndex) { "" })
                confidenceTotal += probabilityOfBest(row, bestValue)
                tokenCount++
            }
            previous = bestIndex
        }
        val confidence = if (tokenCount == 0) 0f else (confidenceTotal / tokenCount).toFloat()
        return LineOutput(result.toString(), confidence)
    }

    private fun probabilityOfBest(row: FloatArray, maximum: Float): Double {
        if (maximum in 0f..1.0001f) {
            val sum = row.sum()
            if (sum in 0.99f..1.01f) return maximum.toDouble()
        }
        var exponentialSum = 0.0
        row.forEach { exponentialSum += exp((it - maximum).toDouble()) }
        return 1.0 / exponentialSum
    }

    override fun close() {
        synchronized(sessionLock) {
            session?.close()
            session = null
        }
    }

    private data class LineOutput(val text: String, val confidence: Float)

    private companion object {
        const val MODEL_ASSET = "handwriting/en_PP-OCRv5_mobile_rec.ort"
        const val DICT_ASSET = "handwriting/en_PP-OCRv5_mobile_rec_dict.txt"
        const val MODEL_FILE = "en_PP-OCRv5_mobile_rec.ort"
        const val EXPECTED_MODEL_BYTES = 7_955_536L
        const val INPUT_HEIGHT = 48
        const val WIDTH_STRIDE = 8
        const val MIN_WIDTH = 16
        const val MAX_WIDTH = 2_048
        const val MIN_CONFIDENCE = 0.20f
    }
}
