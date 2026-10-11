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
        advanced authority awareness basic board bridge capacity cargo certificate certificates certify
        chemical coast competence competency competent completed compliance construction continuous
        convention craft crew deck directorate discharge duties elementary endorsement endorsements engine
        engineer equipment expiry firefighting fitness flag government holder insurance international issued
        issuing maritime manning mariner mariners master medical merchant minimum navigation officer passport
        personal place pollution port proficiency provisions radio rank rating ratings register registrar
        registry regulation regulations rescue resource safe safety seafarer seafarers security shipping
        signature society stcw survival tanker techniques tonnage until valid validity vessel vessels
        watchkeeping
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
        if (lower.length < 4 || isProtected(original)) return original
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

    private val ordinal = Regex("(?i)\\d+(st|nd|rd|th)")

    /**
     * Identifiers, certificate numbers, dates and ordinals are never corrected.
     * A token with digits is eligible only when its letters are not all capitals and
     * its digits are a few known OCR look-alikes (0/1/5/6/8) inside an alphabetic word,
     * for example "d0cument" or "5canned".
     */
    private fun isProtected(original: String): Boolean {
        val letters = original.count { it.isLetter() }
        val digits = original.count { it.isDigit() }
        if (letters == 0) return true
        if (original.filter { it.isLetter() }.all { it.isUpperCase() }) return true
        if (digits == 0) return false
        if (digits > 2 || letters < digits * 3) return true
        if (original.any { it.isDigit() && it !in confusionMap }) return true
        return ordinal.matches(original)
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
