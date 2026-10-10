package com.shawnkowalchuk.milo.platform.nothingrecorded

import com.shawnkowalchuk.milo.core.clock.JumpingPhone
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.platform.clock.ClockWatch
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertTrue

private const val SECOND_MS = 1_000L

/**
 * What the tests of the "nothing recorded" check share on a phone whose date is set a day
 * ahead by hand (ADR-006): the check, its look and its alarm requests are the real ones, on
 * MilO's real clock ([JumpingPhone]), with the real [ClockWatch] to have the alarm asked for
 * again.
 *
 * Android's part is played by hand. The alarm service holds the newest request
 * (`alarmHeld`): one for a time of day is due once the PHONE's clock has passed it, which is
 * what the phone's own event log showed on 2026-10-07, and one that counts from boot is due
 * when its wait is over, whatever the date ([alarmIsDue]). Android's word that the clock was
 * set reaches the watch.
 */
abstract class NothingRecordedClockJumpFixture : NothingRecordedCheckFixture() {
    protected lateinit var phone: JumpingPhone
    protected lateinit var watch: ClockWatch

    /** Wednesday 7 October as on the phone: trips were recorded, the last one at 17:01. */
    protected fun wednesdaysTrips() =
        listOf(trip("2026-10-07T08:05"), trip("2026-10-07T11:44"), trip("2026-10-07T17:01"))

    /** The phone of the test, at [start], and the check's clock on it. */
    protected fun phoneAt(start: String) {
        phone = JumpingPhone(at(start))
        clock = { phone.clock.now() }
        phoneClockAgrees = { phone.clock.agreesWithPhone() }
        clockOnProbation = { phone.clock.onProbation }
        elapsedNow = { phone.elapsedMs }
    }

    /** A process of MilO: the check, and the watch that has its alarm asked for again. */
    protected fun TestScope.process(): NothingRecordedCheck {
        val check = check()
        watch =
            ClockWatch(
                clock = phone.clock,
                askAgain = listOf(check::arm),
                eventLog = EventLogRepository(log),
                crashFileStore = crashFiles,
                scope = backgroundScope,
            )
        watch.start()
        return check
    }

    /** True if Android would deliver the daily alarm now. */
    protected fun alarmIsDue() = checkNotNull(alarmHeld) { "no alarm is asked for" }.isDueOn(phone)

    /** The date is set a day ahead: the alarm is overdue and arrives, with word of the change. */
    protected fun TestScope.dateSetAhead(check: NothingRecordedCheck) {
        phone.setAhead()
        assertTrue(alarmIsDue())
        check.onAlarm {}
        watch.onPhoneClockSet {}
        letItLook()
    }

    /** Some seconds later automatic time puts the clock back, and Android says so. */
    protected fun TestScope.dateSetBack(afterSeconds: Long = 9) {
        phone.advance(afterSeconds * SECOND_MS)
        phone.setBack()
        watch.onPhoneClockSet {}
        letItLook()
    }
}
