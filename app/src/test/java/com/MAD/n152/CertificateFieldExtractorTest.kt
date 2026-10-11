package com.MAD.n152

import org.junit.Assert.assertEquals
import org.junit.Test

class CertificateFieldExtractorTest {
    @Test
    fun extractsCertificateFields() {
        val fields = CertificateFieldExtractor.extract(
            """CARGO SHIP CONSTRUCTION CERTIFICATE
                |Issued by: Government of India
                |This certificate is valid until 19/04/2027.
                |Issued at Mumbai on 22/01/2026.
            """.trimMargin()
        )
        assertEquals("CARGO SHIP CONSTRUCTION CERTIFICATE", fields.certificateName)
        assertEquals("22/01/2026", fields.issueDate)
        assertEquals("Mumbai", fields.issuePort)
        assertEquals("Government of India", fields.issuingAuthority)
        assertEquals("19/04/2027", fields.expiryDate)
    }

    @Test
    fun prefersCertificatesOwnIssuanceLineAndRejectsTruncatedQaDate() {
        val fields = CertificateFieldExtractor.extract(
            """CERTIFICATE OF COMPLIANCE
               |Issued at Chennai on 03/01/2025 as part of the attachment.
               |Issued at Mumbai on 22/01/2026.
               |Expiry Date
               |19/04/2027
            """.trimMargin(),
            answer = { "19/05/202" }
        )
        assertEquals("22/01/2026", fields.issueDate)
        assertEquals("Mumbai", fields.issuePort)
        assertEquals("19/04/2027", fields.expiryDate)
    }

    @Test
    fun invalidQaDateIsNotShownAsCertificateMetadata() {
        val fields = CertificateFieldExtractor.extract("CERTIFICATE OF INSURANCE", answer = { "19/05/202" })
        assertEquals(null, fields.issueDate)
        assertEquals(null, fields.expiryDate)
    }

    @Test
    fun unrelatedDocumentDoesNotAskCertificateModel() {
        val fields = CertificateFieldExtractor.extract("Meeting notes, 22/01/2026") {
            error("Certificate model should not run for ordinary notes")
        }
        assertEquals(0, fields.foundCount)
    }

    @Test
    fun missingBottomDoesNotTurnEquipmentDatesIntoCertificateFields() {
        val fields = CertificateFieldExtractor.extract(
            """ANNUAL TEST CERTIFICATE - GMDSS TWO WAY VHF
               |Issued in accordance with IMO resolutions
               |Expiry Date of Battery
               |30-October-2026
               |Test result N/A
            """.trimMargin(), answer = { "VHF" }
        )
        assertEquals("ANNUAL TEST CERTIFICATE - GMDSS TWO WAY VHF", fields.certificateName)
        assertEquals(null, fields.issueDate)
        assertEquals(null, fields.issuePort)
        assertEquals(null, fields.issuingAuthority)
        assertEquals(null, fields.expiryDate)
    }

    @Test
    fun fullPageUsesCertificateIssuanceAndValidity() {
        val fields = CertificateFieldExtractor.extract(
            """master systems
               |solutions simplified
               |ANNUAL TEST CERTIFICATE - GMDSS TWO WAY VHF
               |Expiry Date of Battery: 30-October-2026
               |Engineer's Name:
               |Place of Issue:
               |ABDUL WAHID
               |HAMRIYAH PORT, SHARJAH, UAE
               |Date of Issue:
               |12July-2025
               |Valid Till:
               |11July-2026
            """.trimMargin()
        )
        assertEquals("HAMRIYAH PORT, SHARJAH, UAE", fields.issuePort)
        assertEquals("12July-2025", fields.issueDate)
        assertEquals("11July-2026", fields.expiryDate)
        assertEquals("master systems", fields.issuingAuthority)
    }

    private val sampleOcr = """MARITIME SAFETY BOARD OF NORTH ESTRAVIA
        |Issued under the provisions of the STCW Convention
        |CERTIFICATE OF PROFICIENCY
        |Proficiency in Survival Craft and Rescue Boats
        |NE-SCRB-2025-04817
        |Certificate No.
        |Date of issue
        |18 June 2025
        |Part of Valmora
        |Place of issue
        |Issuing authority
        |Maritime Safety Board, Valmora
        |Date of expiry
        |17 June 2030
    """.trimMargin()

    @Test
    fun portValueAboveLabelIsPreferredOverAuthorityLine() {
        val fields = CertificateFieldExtractor.extract(sampleOcr)
        assertEquals("Port of Valmora", fields.issuePort)
        assertEquals("Maritime Safety Board, Valmora", fields.issuingAuthority)
        assertEquals("18 June 2025", fields.issueDate)
        assertEquals("17 June 2030", fields.expiryDate)
    }

    @Test
    fun correctedSampleKeepsPortAndAuthorityDistinct() {
        val raw = sampleOcr.replace("Part of Valmora", "Port of Valmora")
        val fields = CertificateFieldExtractor.extract(OcrTextCorrector.correct(raw))
        assertEquals("Port of Valmora", fields.issuePort)
        assertEquals("Maritime Safety Board, Valmora", fields.issuingAuthority)
    }

    @Test
    fun portLabelToleratesOcrConfusions() {
        listOf("Part of issue: Mumbai", "P0rt of issue: Mumbai", "Pori of issue: Mumbai", "Place of issue: Mumbai").forEach { line ->
            val fields = CertificateFieldExtractor.extract("CERTIFICATE OF COMPETENCY\n$line")
            assertEquals(line, "Mumbai", fields.issuePort)
        }
        val misread = CertificateFieldExtractor.extract("CERTIFICATE OF COMPETENCY\nPlace of issue\nP0rt of Kandla")
        assertEquals("Port of Kandla", misread.issuePort)
    }

    @Test
    fun organisationBelowLabelIsOnlyALastResort() {
        val fields = CertificateFieldExtractor.extract(
            """CERTIFICATE OF COMPETENCY
               |Place of issue
               |Directorate General of Shipping, Mumbai
            """.trimMargin()
        )
        assertEquals("Directorate General of Shipping, Mumbai", fields.issuePort)
    }
}
