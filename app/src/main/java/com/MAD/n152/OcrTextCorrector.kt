package com.MAD.n152

/**
 * Conservative, fully offline OCR post-correction.
 *
 * This is intentionally not a generative rewrite model. It only changes a word
 * when it is close to a known common/document word and leaves codes and names
 * alone. A larger word-frequency asset can replace this dictionary later.
 */
object OcrTextCorrector {
    private val dictionary = """
        about above across action after again against agreement all also analysis answer any application area around as asked available average back based because before between both build building business camera can cannot capture captured character characters check choose clear code color column common complete computer condition connect content convert correct correction created current data date document documents done each edit education either electronic email enough example extracted extraction file files final first following for from function future general get give given good has have help here high image images important improve including input install into issue its language large last later line local machine main make many model more most move name need never new next noise number numbers object offline only open output page pages paper parameters part performance phone photo pixels please possible precision prepare printed processing project provide read recognition recognize recognized record result results return run same scanned scanning screen search second select selection send sentence set should show simple since size small smartphone source start state text than that their them then there these they thing this time title to together total training text used user using value very view want way well when which word words work written wrong year your
    """.trimIndent()
        .split(Regex("\\s+"))
        .map(String::lowercase)
        .toSet()

    private val confusionMap = mapOf(
        '0' to 'o',
        '1' to 'l',
        '5' to 's',
        '6' to 'g',
        '8' to 'b'
    )

    fun correct(text: String): String =
        Regex("\\b[\\p{L}0-9]+\\b").replace(text) { match ->
            correctWord(match.value)
        }

    private fun correctWord(original: String): String {
        val lower = original.lowercase()
        if (lower.length < 4 || original.all { it.isUpperCase() } || lower.any { it.isDigit() }) {
            return original
        }
        if (lower in dictionary) return original

        val normalized = lower.map { confusionMap[it] ?: it }.joinToString("")
        val maximumDistance = if (lower.length >= 7) 2 else 1
        val candidates = dictionary.asSequence()
            .map { word -> word to levenshtein(normalized, word) }
            .filter { (_, distance) -> distance <= maximumDistance }
            .sortedBy { (_, distance) -> distance }
            .take(2)
            .toList()

        val best = candidates.firstOrNull() ?: return original
        val secondDistance = candidates.getOrNull(1)?.second ?: Int.MAX_VALUE
        val isClearlyBetter = secondDistance - best.second >= 1
        if (!isClearlyBetter) return original

        return when {
            original.first().isUpperCase() -> best.first.replaceFirstChar { it.uppercase() }
            else -> best.first
        }
    }

    private fun levenshtein(first: String, second: String): Int {
        val previous = IntArray(second.length + 1) { it }
        val current = IntArray(second.length + 1)

        first.forEachIndexed { firstIndex, firstChar ->
            current[0] = firstIndex + 1
            second.forEachIndexed { secondIndex, secondChar ->
                current[secondIndex + 1] = minOf(
                    current[secondIndex] + 1,
                    previous[secondIndex + 1] + 1,
                    previous[secondIndex] + if (firstChar == secondChar) 0 else 1
                )
            }
            for (index in previous.indices) previous[index] = current[index]
        }
        return previous[second.length]
    }
}
