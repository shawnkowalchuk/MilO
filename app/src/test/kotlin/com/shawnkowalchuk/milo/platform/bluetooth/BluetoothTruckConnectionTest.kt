package com.shawnkowalchuk.milo.platform.bluetooth

import com.shawnkowalchuk.milo.platform.bluetooth.ProfileAnswer.NO_ANSWER
import com.shawnkowalchuk.milo.platform.bluetooth.ProfileAnswer.TRUCK_CONNECTED
import com.shawnkowalchuk.milo.platform.bluetooth.ProfileAnswer.TRUCK_NOT_CONNECTED
import com.shawnkowalchuk.milo.platform.bluetooth.TruckReading.Answer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * How the answers of the two Bluetooth profiles become one reading of the truck. Asking the
 * profiles themselves needs a phone; what is made of their answers does not.
 */
class BluetoothTruckConnectionTest {
    @Test
    fun `one profile listing the truck is enough`() {
        // With Android Auto the truck keeps the hands-free profile and sends audio over the
        // cable, so the audio profile says "not connected" for the whole drive.
        assertEquals(
            Answer.CONNECTED,
            readingFromProfiles(TRUCK_CONNECTED, TRUCK_NOT_CONNECTED).answer,
        )
        assertEquals(
            Answer.CONNECTED,
            readingFromProfiles(TRUCK_NOT_CONNECTED, TRUCK_CONNECTED).answer,
        )
        assertEquals(Answer.CONNECTED, readingFromProfiles(TRUCK_CONNECTED, TRUCK_CONNECTED).answer)
    }

    @Test
    fun `a profile that lists the truck is believed even if the other gave no answer`() {
        assertEquals(Answer.CONNECTED, readingFromProfiles(TRUCK_CONNECTED, NO_ANSWER).answer)
        assertEquals(Answer.CONNECTED, readingFromProfiles(NO_ANSWER, TRUCK_CONNECTED).answer)
    }

    @Test
    fun `not connected needs both profiles to say so`() {
        val reading = readingFromProfiles(TRUCK_NOT_CONNECTED, TRUCK_NOT_CONNECTED)

        assertEquals(Answer.NOT_CONNECTED, reading.answer)
    }

    @Test
    fun `a profile that gave no answer could be the truck's, so the reading is unknown`() {
        // During a trip two readings of "not connected" start the grace period. A profile that
        // merely failed to answer must never count as one of them.
        assertEquals(Answer.UNKNOWN, readingFromProfiles(NO_ANSWER, TRUCK_NOT_CONNECTED).answer)
        assertEquals(Answer.UNKNOWN, readingFromProfiles(TRUCK_NOT_CONNECTED, NO_ANSWER).answer)
        assertEquals(Answer.UNKNOWN, readingFromProfiles(NO_ANSWER, NO_ANSWER).answer)
    }

    @Test
    fun `unknown is never taken for connected`() {
        val unknown = TruckReading.unknown("MilO is not allowed to use Bluetooth")

        assertFalse(unknown.connected)
        assertFalse(unknown.known)
    }

    @Test
    fun `every reading says how it was reached`() {
        assertEquals(
            "the hands-free profile lists the truck",
            readingFromProfiles(TRUCK_CONNECTED, TRUCK_NOT_CONNECTED).evidence,
        )
        assertEquals(
            "the audio profile gave no answer",
            readingFromProfiles(TRUCK_NOT_CONNECTED, NO_ANSWER).evidence,
        )
    }
}
