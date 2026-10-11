package com.MAD.n152

import com.google.mlkit.vision.text.Text
import kotlin.math.roundToInt

data class DocumentText(
    val text: String,
    val blocks: Int,
    val lines: Int,
    val words: Int
)

/** Reconstructs paragraph breaks, line breaks and modest indentation from ML Kit geometry. */
object DocumentTextFormatter {
    fun format(result: Text): DocumentText {
        val orderedBlocks = result.textBlocks.sortedWith(
            compareBy<Text.TextBlock> { it.boundingBox?.top ?: Int.MAX_VALUE }
                .thenBy { it.boundingBox?.left ?: Int.MAX_VALUE }
        )
        val allLines = orderedBlocks.flatMap { it.lines }
        val characterWidths = allLines.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            if (line.text.isBlank()) null else box.width().toDouble() / line.text.length
        }.sorted()
        val typicalCharacterWidth = characterWidths
            .getOrNull(characterWidths.size / 2)
            ?.coerceAtLeast(4.0)
            ?: 10.0

        val text = orderedBlocks.joinToString("\n\n") { block ->
            val blockLeftEdge = block.lines.mapNotNull { it.boundingBox?.left }.minOrNull() ?: 0
            block.lines
                .sortedBy { it.boundingBox?.top ?: Int.MAX_VALUE }
                .joinToString("\n") { line ->
                    val lineLeft = line.boundingBox?.left ?: blockLeftEdge
                    val rawIndentation = ((lineLeft - blockLeftEdge) / typicalCharacterWidth)
                        .roundToInt()
                    val indentation = if (rawIndentation >= 2) rawIndentation.coerceAtMost(6) else 0
                    " ".repeat(indentation) + line.text.trimEnd()
                }
        }.trim()

        return DocumentText(
            text = text,
            blocks = orderedBlocks.size,
            lines = allLines.size,
            words = Regex("\\S+").findAll(text).count()
        )
    }
}
