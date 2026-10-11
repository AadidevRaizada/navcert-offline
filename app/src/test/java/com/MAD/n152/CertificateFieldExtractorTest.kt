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
}
