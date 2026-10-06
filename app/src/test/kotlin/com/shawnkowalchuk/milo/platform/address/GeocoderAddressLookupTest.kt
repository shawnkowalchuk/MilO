package com.shawnkowalchuk.milo.platform.address

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val LIMIT_MS = 100L
private const val CALL_MS = 5_000L
private const val NANOS_PER_MS = 1_000_000L

/**
 * The one part of the geocoder lookup that runs without a phone: the time limit around the
 * blocking call of Android 12. The geocoder itself is covered by DEVICE_TEST_CHECKLIST.
 *
 * These tests run on real threads and the real clock, because a blocked thread is what they are
 * about. The margins are wide: a wait of 0.1 s is told apart from one of 5 s.
 */
class GeocoderAddressLookupTest {
    @Test
    fun `a time limit ends the wait for a blocking call that outlasts it`() = runBlocking {
        val startedAt = System.nanoTime()

        val answer =
            withTimeoutOrNull(LIMIT_MS) {
                onBlockingThread {
                    // As Android 12's geocoder call takes an interrupt: it stops waiting, keeps
                    // the interrupt to itself and returns an empty answer.
                    try {
                        Thread.sleep(CALL_MS)
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    emptyList<PlaceParts>()
                }
            }

        val tookMs = (System.nanoTime() - startedAt) / NANOS_PER_MS
        assertNull("What the call returned after the limit was taken for an answer", answer)
        assertTrue("The wait lasted $tookMs ms, as long as the call", tookMs < CALL_MS / 2)
    }

    @Test
    fun `a blocking call that answers in time gives its answer`() = runBlocking {
        val answer = withTimeoutOrNull(CALL_MS) { onBlockingThread { listOf(PlaceParts()) } }

        assertEquals(listOf(PlaceParts()), answer)
    }
}
