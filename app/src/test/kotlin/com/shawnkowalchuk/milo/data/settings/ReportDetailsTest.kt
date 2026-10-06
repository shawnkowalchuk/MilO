package com.shawnkowalchuk.milo.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The four settings of the report: what is stored of a typed text, and what an address is. */
class ReportDetailsTest {
    @Test
    fun `an ordinary email address is one`() {
        val addresses =
            listOf(
                "accounts@example.ca",
                "a@b.co",
                "first.last@example.com",
                "accounts+mileage@payroll.example-firm.com",
                "o'brien@example.ie",
                "AP_Team-2@sub.domain.example.org",
                "9lives@123.example.io",
            )
        for (address in addresses) assertTrue(address, isEmailAddress(address))
    }

    @Test
    fun `a slip of the thumb is caught`() {
        val slips =
            listOf(
                "",
                "accounts",
                "accounts@",
                "@example.ca",
                "accounts@example",
                "accounts@example.",
                "accounts@.ca",
                "accounts@example..ca",
                "accounts@@example.ca",
                "accounts@example@ca",
                "accounts example.ca",
                "accounts@example,ca",
                "accounts@exam ple.ca",
                " accounts@example.ca",
                "accounts@example.ca ",
                "accounts@example.c",
                "accounts@example.c0m",
                ".accounts@example.ca",
                "accounts.@example.ca",
                "acc..ounts@example.ca",
                "accounts@-example.ca",
                "accounts@example-.ca",
                "accounts@exam_ple.ca",
            )
        for (slip in slips) assertFalse("\"$slip\"", isEmailAddress(slip))
    }

    @Test
    fun `two addresses, or a name beside the address, are not one address`() {
        val notOne =
            listOf(
                "a@example.ca,b@example.ca",
                "a@example.ca;b@example.ca",
                "a@example.ca b@example.ca",
                "Accounts <accounts@example.ca>",
                "\"Accounts\"@example.ca",
                "mailto:accounts@example.ca",
                "accounts@example.ca\n",
            )
        for (text in notOne) assertFalse("\"$text\"", isEmailAddress(text))
    }

    @Test
    fun `an address longer than email allows is refused`() {
        val longestLocal = "a".repeat(64)
        val fits = "$longestLocal@example.ca"
        val start = "accounts@" + "d".repeat(63) + "." + "e".repeat(63) + "." + "f".repeat(63) + "."
        val atTheLimit = start + "g".repeat(MAX_EMAIL_LENGTH - start.length - 3) + ".ca"

        assertTrue(isEmailAddress(fits))
        assertFalse(isEmailAddress("a$fits"))
        assertEquals(MAX_EMAIL_LENGTH, atTheLimit.length)
        assertTrue(isEmailAddress(atTheLimit))
        assertFalse(isEmailAddress("x$atTheLimit"))
    }

    @Test
    fun `a typed text is stored without the spaces around it, and nothing is stored as nothing`() {
        assertEquals("Sam Driver", reportTextOrNull("  Sam Driver "))
        assertEquals("Sam  Driver", reportTextOrNull("Sam  Driver"))
        assertNull(reportTextOrNull(""))
        assertNull(reportTextOrNull("   "))
    }

    @Test
    fun `only a trimmed text that fits the report can be stored as it is`() {
        assertTrue(isReportText("Sam Driver"))
        assertTrue(isReportText("a".repeat(MAX_REPORT_TEXT_LENGTH)))
        assertFalse(isReportText("a".repeat(MAX_REPORT_TEXT_LENGTH + 1)))
        assertFalse(isReportText(" Sam Driver"))
        assertFalse(isReportText("Sam Driver "))
        assertFalse(isReportText(""))
        assertFalse(isReportText("  "))
    }

    @Test
    fun `a PDF needs the name, and sending needs the address as well`() {
        val nothing = MiloSettings()
        val named = MiloSettings(reportName = "Sam Driver")
        val addressed = MiloSettings(accountantEmail = "accounts@example.ca")
        val both = named.copy(accountantEmail = "accounts@example.ca")

        assertEquals(listOf(MissingDetail.NAME), nothing.missingForPdf())
        assertEquals(emptyList<MissingDetail>(), named.missingForPdf())
        assertEquals(
            listOf(MissingDetail.NAME, MissingDetail.ACCOUNTANT_EMAIL),
            nothing.missingForSending(),
        )
        assertEquals(listOf(MissingDetail.ACCOUNTANT_EMAIL), named.missingForSending())
        assertEquals(listOf(MissingDetail.NAME), addressed.missingForSending())
        assertEquals(emptyList<MissingDetail>(), both.missingForSending())
    }

    @Test
    fun `the company and the vehicle are never needed`() {
        val bare = MiloSettings(reportName = "Sam Driver", accountantEmail = "accounts@example.ca")

        assertNull(bare.reportCompany)
        assertNull(bare.reportVehicle)
        assertEquals(emptyList<MissingDetail>(), bare.missingForSending())
    }
}
