package com.shawnkowalchuk.milo.platform.driving

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the event log says about the driving alert. The lines are the only evidence of whether
 * driving detection reaches MilO on the phone, so their wording is pinned here.
 */
class DrivingLogTextTest {
    // Tuesday 2026-10-06, 12:00 UTC.
    private val noon = 1_791_288_000_000L
    private val moment =
        drivingMoment(
            reports = listOf(VehicleReport(entered = true, ageMs = 61_500)),
            nowMs = noon,
            zone = ZoneId.of("UTC"),
        )

    @Test
    fun `every report of a delivery is named, oldest first, with its age`() {
        val reports =
            listOf(
                VehicleReport(entered = false, ageMs = 95_000),
                VehicleReport(entered = true, ageMs = 3_200),
            )

        assertEquals(
            "Driving detection: left a vehicle 95 s ago, then entered a vehicle 3 s ago. " +
                "Alert shown: no trip is being recorded and the truck is not connected",
            judgedText(reports, DrivingVerdict.DRIVING_WITHOUT_THE_TRUCK, seen = true),
        )
    }

    @Test
    fun `a report that is too old says what the limit is`() {
        assertEquals(
            "Driving detection: entered a vehicle 7200 s ago. Not acted on: the report is more " +
                "than 5 min old and says nothing about now",
            judgedText(
                listOf(VehicleReport(entered = true, ageMs = 7_200_000)),
                DrivingVerdict.REPORT_TOO_OLD,
                seen = true,
            ),
        )
    }

    @Test
    fun `a delivery without a vehicle in it still leaves a line`() {
        assertEquals(
            "Driving detection: a report that names no vehicle. Nothing done",
            judgedText(emptyList(), DrivingVerdict.NOTHING_REPORTED, seen = true),
        )
    }

    @Test
    fun `only an alert that was shown can have gone unseen`() {
        val unseen = judgedText(moment.reports, DrivingVerdict.DRIVING_TRUCK_UNKNOWN, seen = false)
        val quiet = judgedText(moment.reports, DrivingVerdict.OUTSIDE_WORK_HOURS, seen = false)

        assertTrue(unseen.endsWith("so nobody saw it"))
        assertTrue(quiet.endsWith("No alert: it is outside the work hours"))
    }

    @Test
    fun `each of the reasons that came with the review has words of its own`() {
        fun words(verdict: DrivingVerdict) =
            judgedText(moment.reports, verdict, seen = true).substringAfter("61 s ago. ")

        assertEquals("No alert: no truck is paired", words(DrivingVerdict.NO_TRUCK_PAIRED))
        assertEquals(
            "No alert: a trip ended less than 5 min ago",
            words(DrivingVerdict.TRIP_JUST_ENDED),
        )
        assertEquals(
            "No new alert: the last one was less than 30 min ago",
            words(DrivingVerdict.ALERTED_RECENTLY),
        )
    }

    @Test
    fun `the detail lists every fact the decision went by`() {
        assertEquals(
            """
            The driving alert is switched on.
            A trip is being recorded: no.
            A truck is paired: yes.
            The truck: not connected (neither profile lists it).
            A trip started now would be saved as Business: it started on a Tue at 12:00:00; that day's hours are 08:00 to 16:30 (UTC).
            The last trip ended: never.
            The last alert: never.
            """.trimIndent(),
            momentText(moment, truckEvidence = "neither profile lists it", notes = emptyList()),
        )
    }

    @Test
    fun `the last trip and the last alert are given in minutes that have passed`() {
        val text =
            momentText(
                moment.copy(
                    truckPaired = false,
                    lastTripEndedAtMs = noon - 299_000,
                    lastAlertAtMs = noon - 30 * MINUTE_MS,
                ),
                truckEvidence = "no truck is paired",
                notes = emptyList(),
            )

        assertTrue(text.contains("A truck is paired: no."))
        // 4 min 59 s: inside the five minutes, and the line shows it.
        assertTrue(text.contains("The last trip ended: 4 min ago."))
        assertTrue(text.endsWith("The last alert: 30 min ago."))
    }

    @Test
    fun `a stored time the clock has not reached is said as it is`() {
        val text = momentText(moment.copy(lastAlertAtMs = noon + 1), "no answer", emptyList())

        assertTrue(
            text.endsWith("The last alert: at a time the phone's clock has not reached yet."),
        )
    }

    @Test
    fun `what went wrong on the way is said first`() {
        val notes = listOf("The settings cannot be read.", "The time could not be stored.")

        val text = momentText(moment, "no answer", notes)

        assertTrue(
            text.startsWith(
                "The settings cannot be read.\nThe time could not be stored.\n" +
                    "The driving alert is switched",
            ),
        )
    }

    @Test
    fun `the line about watching names what stands in the way`() {
        val source = "process start"

        assertEquals(
            "Driving alert: watching for driving (process start)",
            watchText(DrivingWatch(alertEnabled = true, permissionGranted = true), source),
        )
        assertEquals(
            "Driving alert: not watching for driving (process start): it is switched off in " +
                "Settings",
            watchText(DrivingWatch(alertEnabled = false, permissionGranted = true), source),
        )
        assertEquals(
            "Driving alert: not watching for driving (process start): the Physical activity " +
                "permission is not granted",
            watchText(DrivingWatch(alertEnabled = true, permissionGranted = false), source),
        )
    }
}
