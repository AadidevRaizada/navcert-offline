package com.MAD.n152

data class CertificateFields(
    val certificateName: String? = null,
    val issueDate: String? = null,
    val issuePort: String? = null,
    val issuingAuthority: String? = null,
    val expiryDate: String? = null
) {
    val foundCount: Int get() = listOf(certificateName, issueDate, issuePort, issuingAuthority, expiryDate).count { !it.isNullOrBlank() }
}

object CertificateFieldExtractor {
    private val certificateCue = Regex("(?i)\\b(certificate|attestation|licen[cs]e|insurance|registry|registration|document of compliance)\\b")
    private val issueEvidence = Regex("(?i)\\b(date of issue|date issued|issue date|issued at|issued on)\\b")
    private val authorityEvidence = Regex("(?i)\\b(issuing authority|issued by|on behalf of|government of|register of shipping)\\b")
    private val organizationName = Regex("(?i)\\b(government|authority|register|registry|society|bureau|marine|maritime|shipping|insurance|systems|services|limited|ltd)\\b")
    private val expiryEvidence = Regex("(?i)\\b(expiry date|date of expiry|valid until|valid till|expires on)\\b")
    private val equipmentDate = Regex("(?i)\\b(battery|cells?|equipment)\\b")
    private const val month = "(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)"
    private val date = Regex(
        "(?:\\d{1,2}[./-]\\d{1,2}[./-](?:\\d{4}|\\d{2})(?!\\d)|\\d{1,2}(?:st|nd|rd|th)?[-\\s]*$month,?[-\\s]+\\d{4}|$month\\.?\\s+\\d{1,2},?\\s+\\d{4})",
        RegexOption.IGNORE_CASE
    )

    fun extract(text: String, fallbackTitle: String = "", answer: ((String) -> String?)? = null): CertificateFields {
        if (!certificateCue.containsMatchIn(text.take(1500) + " " + fallbackTitle)) return CertificateFields()
        val lines = text.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        fun qa(question: String) = answer?.invoke(question)?.clean()?.takeIf { it.length in 2..160 }
        fun qaDate(question: String) = qa(question)?.let { date.find(it)?.value }
        val hasExpiryEvidence = lines.any { expiryEvidence.containsMatchIn(it) && !equipmentDate.containsMatchIn(it) }

        val issuedAt = Regex("(?im)^issued\\s+at\\s+(.+?)\\s+on\\s+(${date.pattern})").findAll(text).lastOrNull()
        val name = lines.take(60).firstOrNull {
            it.length in 12..140 && it.lowercase() !in setOf("certificate", "attestation", "licence", "license") &&
                Regex("(?i)\\b(certificate|attestation|licen[cs]e)\\b").containsMatchIn(it) &&
                !Regex("(?i)\\b(no\\.?|number|valid|issue|expiry|conditions|this certificate|annexure to)\\b").containsMatchIn(it)
        } ?: qa("What is the full name or title of this certificate?") ?: fallbackTitle.takeIf(String::isNotBlank)

        return CertificateFields(
            certificateName = name?.clean(),
            issueDate = issuedAt?.groupValues?.get(2)
                ?: labeledDate(lines, "date\\s+(?:of\\s+issue|issued)|issue\\s+date|issued\\s+on")
                ?: if (issueEvidence.containsMatchIn(text)) qaDate("What is the date of issue of this certificate?") else null,
            issuePort = issuedAt?.groupValues?.get(1)?.clean()
                ?: labeledPort(lines),
            issuingAuthority = lines.take(40).firstOrNull { it.equals("by Indian Register of Shipping", true) }
                ?.substring(3) ?: labeledValue(lines, "issuing\\s+authority|issued\\s+by")
                ?: issuerFromHeader(lines, name)
                ?: if (authorityEvidence.containsMatchIn(text)) qa("Which authority or organization issued this certificate?") else null,
            expiryDate = labeledDate(lines, "expiry(?:\\s+date|\\s+dated)?|date\\s+of\\s+expiry|valid\\s+(?:until|till)|expires\\s+on")
                ?: if (hasExpiryEvidence) qaDate("What is the expiry date or valid-until date of this certificate?") else null
        )
    }

    private fun labeledDate(lines: List<String>, label: String): String? {
        val marker = Regex(label, RegexOption.IGNORE_CASE)
        lines.forEachIndexed { index, line ->
            if (marker.containsMatchIn(line) && !equipmentDate.containsMatchIn(line)) {
                date.find(line)?.value?.let { return it.clean() }
                lines.getOrNull(index + 1)?.let { next -> date.find(next)?.value?.let { return it.clean() } }
            }
        }
        return null
    }

    private fun labeledValue(lines: List<String>, label: String): String? {
        val marker = Regex("(?i)^(?:.*?\\b)?(?:$label)\\b\\s*[:.-]?\\s*(.*)${'$'}")
        lines.forEachIndexed { index, line ->
            val match = marker.find(line) ?: return@forEachIndexed
            val sameLine = match.groupValues[1].clean().takeIf { it.length > 1 }
            if (sameLine != null) return sameLine
            lines.getOrNull(index + 1)?.clean()?.takeIf { it.length > 1 }?.let { return it }
        }
        return null
    }

    private fun labeledPort(lines: List<String>): String? {
        val marker = Regex("(?i)\\b(place|port)\\s+of\\s+issue\\b")
        val placeCue = Regex("(?i)\\b(port|harbou?r)\\b|,")
        lines.forEachIndexed { index, line ->
            val match = marker.find(line) ?: return@forEachIndexed
            val sameLine = line.substring(match.range.last + 1).clean()
            if (sameLine.length > 1) return sameLine
            val candidates = lines.drop(index + 1).take(3)
                .takeWhile { !it.endsWith(':') && !Regex("(?i)^(date|valid|expiry|signature)\\b").containsMatchIn(it) }
                .filter { it.length in 2..80 }
            candidates.firstOrNull { placeCue.containsMatchIn(it) }?.let { return it.clean() }
            if (candidates.size == 1) return candidates.first().clean()
        }
        return null
    }

    private fun issuerFromHeader(lines: List<String>, name: String?): String? {
        val titleIndex = lines.indexOfFirst { it.equals(name, ignoreCase = true) }
        if (titleIndex !in 1..8) return null
        return lines.take(titleIndex).firstOrNull {
            it.length in 5..80 && organizationName.containsMatchIn(it) && !certificateCue.containsMatchIn(it)
        }?.clean()
    }

    private fun String.clean() = trim().trim(':', '-', '.', ',', ';').replace(Regex("\\s+"), " ")
}
