package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatDistance
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripEdit
import com.shawnkowalchuk.milo.data.trip.TypedAddress
import com.shawnkowalchuk.milo.data.trip.TypedTrip
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The edit form as plain values: what it starts with, and what picked and typed values mean as
 * stored times, metres and addresses. Times are Edmonton wall-clock times.
 */
class TripFormTest {
    private val edmonton = ZoneId.of("America/Edmonton")

    private fun local(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(edmonton).toInstant().toEpochMilli()

    /** A recorded trip: exact to the second, with a distance that is no round figure. */
    private val stored =
        Trip(
            id = 12,
            startedAtMs = local("2026-10-05T08:14:27") + 531,
            endedAtMs = local("2026-10-05T08:39:02"),
            status = TripStatus.FINISHED,
            startedBy = TripStartCause.TRUCK,
            truckSeen = true,
            distanceMetres = 12_344.7,
            startAddress = "12 Shop Rd, Edmonton",
            endAddress = null,
            category = TripCategory.BUSINESS,
        )

    private val opened = formFor(stored, edmonton, DistanceUnit.KILOMETRES)

    // ---- What the form starts with --------------------------------------------------------------

    @Test
    fun `the form for a missed trip is empty and on today's date`() {
        val form =
            blankForm(
                nowMs = local("2026-10-06T14:32:10"),
                zone = edmonton,
                unit = DistanceUnit.KILOMETRES,
            )

        // No time, no address, no distance and no category is filled in for him.
        assertEquals(
            TripForm(date = LocalDate.of(2026, 10, 6), unit = DistanceUnit.KILOMETRES),
            form,
        )
        assertNull(form.startedAtMs(stored = null, edmonton))
        assertNull(form.toTypedTrip(edmonton))
    }

    @Test
    fun `today is worked out in the phone's time zone`() {
        // 23:30 on the 5th in Edmonton is already the 6th in UTC.
        val lateEvening = local("2026-10-05T23:30:00")

        assertEquals(
            LocalDate.of(2026, 10, 5),
            blankForm(lateEvening, edmonton, DistanceUnit.KILOMETRES).date,
        )
        assertEquals(
            LocalDate.of(2026, 10, 6),
            blankForm(lateEvening, ZoneId.of("UTC"), DistanceUnit.KILOMETRES).date,
        )
    }

    @Test
    fun `the form for a stored trip shows its day and its two times to the minute`() {
        assertEquals(
            TripForm(
                date = LocalDate.of(2026, 10, 5),
                start = LocalTime.of(8, 14),
                end = LocalTime.of(8, 39),
                unit = DistanceUnit.KILOMETRES,
            ),
            opened,
        )
    }

    @Test
    fun `a stored trip that ran past midnight keeps ending on the next day`() {
        val overnight = stored.copy(endedAtMs = local("2026-10-06T00:10:30"))

        val form = formFor(overnight, edmonton, DistanceUnit.KILOMETRES)

        assertEquals(1L, form.endDayOffset)
        assertEquals(LocalTime.of(0, 10), form.end)
        // A new end time is on that next day too, and a new date moves both ends.
        assertEquals(
            local("2026-10-06T00:25:00"),
            form.copy(end = LocalTime.of(0, 25)).endedAtMs(overnight, edmonton),
        )
        assertEquals(
            local("2026-10-03T00:25:00"),
            form
                .copy(date = LocalDate.of(2026, 10, 2), end = LocalTime.of(0, 25))
                .endedAtMs(overnight, edmonton),
        )
    }

    // ---- Opening and saving changes nothing -----------------------------------------------------

    @Test
    fun `a form that was opened and not touched asks for nothing`() {
        assertEquals(TripEdit(), opened.toEdit(stored, edmonton))
    }

    @Test
    fun `an untouched time stays exact to the millisecond`() {
        assertEquals(stored.startedAtMs, opened.startedAtMs(stored, edmonton))
        assertEquals(stored.endedAtMs, opened.endedAtMs(stored, edmonton))
    }

    @Test
    fun `a time that is changed and put back is the stored time again`() {
        val changed = opened.copy(start = LocalTime.of(8, 20))
        val putBack = changed.copy(start = LocalTime.of(8, 14))

        assertEquals(local("2026-10-05T08:20:00"), changed.startedAtMs(stored, edmonton))
        // Not 08:14:00: the 27.531 seconds are still there.
        assertEquals(stored.startedAtMs, putBack.startedAtMs(stored, edmonton))
        assertEquals(TripEdit(), putBack.toEdit(stored, edmonton))
    }

    @Test
    fun `the same minute on another day is another time`() {
        val moved = opened.copy(date = LocalDate.of(2026, 10, 2))

        assertEquals(local("2026-10-02T08:14:00"), moved.startedAtMs(stored, edmonton))
        assertEquals(local("2026-10-02T08:39:00"), moved.endedAtMs(stored, edmonton))
    }

    @Test
    fun `typing the distance and the address that are shown asks for nothing`() {
        val retyped = opened.copy(kilometres = "12.3", from = " 12 Shop Rd, Edmonton ", to = "")

        // "12.3" is what the form showed for 12 344.7 m: not a change of 44.7 m.
        assertEquals(TripEdit(), retyped.toEdit(stored, edmonton))
        assertEquals(TripEdit(), opened.copy(kilometres = "12,30").toEdit(stored, edmonton))
    }

    // ---- The same in miles (2026-10-07) ----------------------------------------------------------

    private val openedInMiles = formFor(stored, edmonton, DistanceUnit.MILES)

    @Test
    fun `a trip opened in miles and saved untouched keeps its stored metres, to the last one`() {
        assertEquals(TripEdit(), openedInMiles.toEdit(stored, edmonton))
        assertEquals(TypedDistance.Metres(12_344.7), openedInMiles.distance(stored))
        assertEquals(DistanceUnit.MILES, openedInMiles.unit)
    }

    @Test
    fun `in either unit an untouched distance, or the shown one typed again, changes nothing`() {
        // Distances that round awkwardly in one unit or the other, and one typed in miles.
        val awkward =
            listOf(0.0, 49.0, 300.0, 12_349.999, 12_350.0, 100_050.0, 1_609.344, 563.2704, 9.8e5)
        for (metres in awkward) {
            val trip = stored.copy(distanceMetres = metres)
            for (unit in DistanceUnit.entries) {
                val form = formFor(trip, edmonton, unit)
                // The figure the field shows, with a dot and with a comma.
                val shown = formatDistance(metres, unit, Locale.ROOT)
                val what = "$metres m in $unit, shown as $shown"

                assertEquals(what, TripEdit(), form.toEdit(trip, edmonton))
                assertEquals(what, TypedDistance.Metres(metres), form.distance(trip))
                assertEquals(what, TripEdit(), form.copy(kilometres = shown).toEdit(trip, edmonton))
                assertEquals(
                    what,
                    TripEdit(),
                    form.copy(kilometres = shown.replace('.', ',')).toEdit(trip, edmonton),
                )
                assertFalse(what, form.copy(kilometres = shown).holdsUnsavedWork(form, trip))
            }
        }
    }

    @Test
    fun `in miles the form shows miles, and another figure typed is stored in metres`() {
        // 12 344.7 m is shown as 7.7 mi.
        assertEquals(TripEdit(), openedInMiles.copy(kilometres = "7.7").toEdit(stored, edmonton))
        // 8 mi is 12 874.752 m: a mile is 1 609.344 m.
        assertEquals(
            TripEdit(distanceMetres = 12_874.752),
            openedInMiles.copy(kilometres = "8").toEdit(stored, edmonton),
        )
        // Typed more exactly than the form shows, it is a change, as it is in kilometres.
        assertEquals(
            TripEdit(distanceMetres = 12_343.66848),
            openedInMiles.copy(kilometres = "7.67").toEdit(stored, edmonton),
        )
    }

    @Test
    fun `the same text typed is another distance in the other unit`() {
        assertEquals(TypedDistance.Metres(12_400.0), parseDistance("12.4", DistanceUnit.KILOMETRES))
        assertEquals(TypedDistance.Metres(19_955.8656), parseDistance("12.4", DistanceUnit.MILES))
        assertEquals(TypedDistance.Metres(19_955.8656), parseDistance(" 12,4 ", DistanceUnit.MILES))
        assertEquals(TypedDistance.Metres(0.0), parseDistance("0", DistanceUnit.MILES))
        assertEquals(TypedDistance.NotANumber, parseDistance("12 mi", DistanceUnit.MILES))
        assertEquals(TypedDistance.Missing, parseDistance(" ", DistanceUnit.MILES))
        // The figure the 12.3 km of the kilometre form would be is not what "12.3" means here.
        assertEquals(
            TripEdit(distanceMetres = 19_794.9312),
            openedInMiles.copy(kilometres = "12.3").toEdit(stored, edmonton),
        )
    }

    @Test
    fun `a missed trip typed in miles is stored in metres`() {
        val form =
            blankForm(local("2026-10-06T14:32:10"), edmonton, DistanceUnit.MILES).copy(
                start = LocalTime.of(9, 0),
                end = LocalTime.of(9, 40),
                kilometres = "12.3",
            )

        assertEquals(19_794.9312, form.toTypedTrip(edmonton)?.distanceMetres)
    }

    // ---- What a change asks for -----------------------------------------------------------------

    @Test
    fun `only what was changed is asked for`() {
        val form =
            opened.copy(
                end = LocalTime.of(8, 45),
                kilometres = "13",
                to = "48 Main St, Leduc",
                chosenCategory = TripCategory.PERSONAL,
            )

        assertEquals(
            TripEdit(
                endedAtMs = local("2026-10-05T08:45:00"),
                distanceMetres = 13_000.0,
                endAddress = TypedAddress("48 Main St, Leduc"),
                category = TripCategory.PERSONAL,
            ),
            form.toEdit(stored, edmonton),
        )
    }

    @Test
    fun `a distance typed more exactly than the form shows is a change`() {
        assertEquals(
            TripEdit(distanceMetres = 12_340.0),
            opened.copy(kilometres = "12.34").toEdit(stored, edmonton),
        )
    }

    @Test
    fun `an address that is emptied is asked for as no address`() {
        assertEquals(
            TripEdit(startAddress = TypedAddress(null)),
            opened.copy(from = "   ").toEdit(stored, edmonton),
        )
    }

    @Test
    fun `an address is stored on one line, trimmed, and never longer than the form allows`() {
        assertEquals("12 Shop Rd, Edmonton", addressAsStored("  12 Shop Rd,\n  Edmonton \t"))
        assertNull(addressAsStored(" \n "))
        assertEquals(MAX_ADDRESS_LENGTH, addressAsStored("a".repeat(500))?.length)
    }

    // ---- A typed distance -----------------------------------------------------------------------

    @Test
    fun `a distance is read with a dot or a comma, and never with a thousands separator`() {
        assertEquals(TypedDistance.Metres(12_400.0), parseDistance("12.4", DistanceUnit.KILOMETRES))
        assertEquals(
            TypedDistance.Metres(12_400.0),
            parseDistance(" 12,4 ", DistanceUnit.KILOMETRES),
        )
        assertEquals(TypedDistance.Metres(7_000.0), parseDistance("7", DistanceUnit.KILOMETRES))
        assertEquals(TypedDistance.Metres(7_000.0), parseDistance("7.", DistanceUnit.KILOMETRES))
        assertEquals(TypedDistance.Metres(500.0), parseDistance(".5", DistanceUnit.KILOMETRES))
        assertEquals(TypedDistance.Metres(0.0), parseDistance("0", DistanceUnit.KILOMETRES))
        assertEquals(
            TypedDistance.Metres(12_345.0),
            parseDistance("12.345", DistanceUnit.KILOMETRES),
        )
        // One and a bit kilometres, not a thousand and something.
        assertEquals(TypedDistance.Metres(1_234.0), parseDistance("1,234", DistanceUnit.KILOMETRES))
        assertEquals(TypedDistance.Metres(-3_000.0), parseDistance("-3", DistanceUnit.KILOMETRES))
    }

    @Test
    fun `what is not a distance is said to be none`() {
        assertEquals(TypedDistance.Missing, parseDistance("", DistanceUnit.KILOMETRES))
        assertEquals(TypedDistance.Missing, parseDistance("   ", DistanceUnit.KILOMETRES))
        for (text in listOf("abc", "12 km", "1.2.3", "1,234.5", "12.3456", ".", "-", "1e3", "١٢")) {
            assertEquals(
                text,
                TypedDistance.NotANumber,
                parseDistance(text, DistanceUnit.KILOMETRES),
            )
        }
    }

    @Test
    fun `a stored distance that cannot be written does not stop a new one from being typed`() {
        // Storage should never hold one. If it did, the form must still take a correction.
        val broken = stored.copy(distanceMetres = Double.POSITIVE_INFINITY)

        assertEquals(
            TypedDistance.Metres(12_000.0),
            opened.copy(kilometres = "12").distance(broken),
        )
    }

    @Test
    fun `an untouched distance field stands for the stored distance, or for none`() {
        assertEquals(TypedDistance.Metres(12_344.7), opened.distance(stored))
        assertEquals(
            TypedDistance.Missing,
            TripForm(date = opened.date, unit = DistanceUnit.KILOMETRES).distance(stored = null),
        )
    }

    // ---- A trip that is added -------------------------------------------------------------------

    @Test
    fun `a filled-in form for a missed trip is the trip as typed`() {
        val form =
            TripForm(
                date = LocalDate.of(2026, 10, 5),
                start = LocalTime.of(9, 0),
                end = LocalTime.of(9, 40),
                from = " Shop ",
                kilometres = "23,4",
                unit = DistanceUnit.KILOMETRES,
            )

        assertEquals(
            TypedTrip(
                startedAtMs = local("2026-10-05T09:00:00"),
                endedAtMs = local("2026-10-05T09:40:00"),
                distanceMetres = 23_400.0,
                startAddress = "Shop",
                endAddress = null,
                category = null,
            ),
            form.toTypedTrip(edmonton),
        )
    }

    @Test
    fun `a missed trip without a time or a readable distance is not a trip yet`() {
        val form =
            TripForm(
                date = LocalDate.of(2026, 10, 5),
                start = LocalTime.of(9, 0),
                end = LocalTime.of(9, 40),
                kilometres = "23.4",
                unit = DistanceUnit.KILOMETRES,
            )

        assertNull(form.copy(start = null).toTypedTrip(edmonton))
        assertNull(form.copy(end = null).toTypedTrip(edmonton))
        assertNull(form.copy(kilometres = null).toTypedTrip(edmonton))
        assertNull(form.copy(kilometres = "far").toTypedTrip(edmonton))
    }

    // ---- The clocks change ----------------------------------------------------------------------

    @Test
    fun `a time in the hour the clocks skip is moved on by that hour`() {
        // Sunday 8 March 2026: 02:00 to 03:00 does not exist in Edmonton.
        val form =
            TripForm(
                date = LocalDate.of(2026, 3, 8),
                start = LocalTime.of(2, 30),
                end = LocalTime.of(4, 0),
                unit = DistanceUnit.KILOMETRES,
            )

        assertEquals(local("2026-03-08T03:30:00"), form.startedAtMs(stored = null, edmonton))
    }

    @Test
    fun `a stored time in the hour that happens twice is kept as it is`() {
        // Sunday 1 November 2026: 01:30 happens twice. The second one is an hour later.
        val second = local("2026-11-01T01:30:00") + 3_600_000
        val trip = stored.copy(startedAtMs = second, endedAtMs = second + 1_200_000)

        val form = formFor(trip, edmonton, DistanceUnit.KILOMETRES)

        assertEquals(LocalTime.of(1, 30), form.start)
        assertEquals(second, form.startedAtMs(trip, edmonton))
        assertEquals(TripEdit(), form.toEdit(trip, edmonton))
    }
}
