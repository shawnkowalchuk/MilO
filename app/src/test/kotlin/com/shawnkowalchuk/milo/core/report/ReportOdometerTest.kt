package com.shawnkowalchuk.milo.core.report

import com.shawnkowalchuk.milo.core.odometer.OdometerFigure
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.odometer.OdometerSpan
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatMediumDay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The truck's odometer at the start and the end of the period (Shawn's choice of 2026-10-07:
 * "Start and end, marked if estimated"): two figures on a tile under the sender's, a figure
 * MilO worked out carrying "est.", and a note that says what that means.
 */
class ReportOdometerTest {
    private val reading =
        OdometerReading(edmonton("2026-10-01T07:00"), 120_000, DistanceUnit.KILOMETRES)

    private fun figure(km: Long, estimated: Boolean) = OdometerFigure(
        km,
        DistanceUnit.KILOMETRES,
        reading,
        drivenTenths = 0,
        estimated = estimated,
    )

    private fun withOdometer(start: OdometerFigure, end: OdometerFigure): MileageReport =
        report(listOf(trip(DAY_ONE, "08:14")))
            .copy(odometers = listOf(VehicleOdometer(null, OdometerSpan(start, end))))

    @Test
    fun `the start and the end are printed with their days, an estimate marked`() {
        val printed =
            printedReport(
                withOdometer(figure(120_000, false), figure(121_234, true)),
                WORDS,
                FORMAT,
            )

        // The day as the phone's language writes it; Java's versions differ in the details
        // ("Oct 1" or "Oct. 1"), so it is not spelled out here.
        val first = formatMediumDay(LocalDate.of(2026, 10, 1), FORMAT.locale)
        val last = formatMediumDay(LocalDate.of(2026, 10, 31), FORMAT.locale)
        assertEquals(
            listOf(
                "Odometer, $first" to "120,000 km",
                "Odometer, $last" to "121,234 km est.",
            ),
            printed.odometer,
        )
        assertTrue(WORDS.odometerNote in printed.notes)
    }

    @Test
    fun `two readings as typed need no note`() {
        val printed =
            printedReport(
                withOdometer(figure(120_000, false), figure(121_234, false)),
                WORDS,
                FORMAT,
            )

        assertFalse(WORDS.odometerNote in printed.notes)
    }

    @Test
    fun `with several vehicles each one's two figures name it`() {
        val report =
            withOdometer(figure(120_000, false), figure(121_234, false)).copy(
                odometers =
                    listOf(
                        VehicleOdometer(
                            "Work truck",
                            OdometerSpan(figure(120_000, false), figure(121_234, false)),
                        ),
                        VehicleOdometer(
                            "Van",
                            OdometerSpan(figure(50_000, false), figure(50_100, true)),
                        ),
                    ),
            )

        val printed = printedReport(report, WORDS, FORMAT)

        val first = formatMediumDay(LocalDate.of(2026, 10, 1), FORMAT.locale)
        assertEquals(4, printed.odometer.size)
        assertEquals("Work truck, Odometer, $first" to "120,000 km", printed.odometer[0])
        assertEquals("Van, Odometer, $first" to "50,000 km", printed.odometer[2])
        assertTrue(WORDS.odometerNote in printed.notes)
    }

    @Test
    fun `without a reading there is no odometer on the report`() {
        val printed = printedReport(report(listOf(trip(DAY_ONE, "08:14"))), WORDS, FORMAT)

        assertTrue(printed.odometer.isEmpty())
        assertFalse(WORDS.odometerNote in printed.notes)
    }

    @Test
    fun `the figures stand on the first page, under the sender`() {
        val page = layout(withOdometer(figure(120_000, false), figure(121_234, true))).first()

        val start = page.find("120,000 km")
        val end = page.find("121,234 km est.")
        assertEquals(start.baseline, end.baseline, 0.01f)
        assertTrue(start.baseline > page.find(SENDER.name).baseline)
        // The note wraps over lines of its own; its first line starts with what it explains.
        assertTrue(page.texts.any { it.text.startsWith("est.:") })
    }
}
