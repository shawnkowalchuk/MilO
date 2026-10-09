package com.shawnkowalchuk.milo.platform.reminder

import com.shawnkowalchuk.milo.core.clock.JumpingPhone
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.platform.clock.ClockWatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertTrue

private const val SECOND_MS = 1_000L

/**
 * What the tests of the monthly reminder share on a phone whose date is set a day ahead by hand
 * (ADR-005): the reminder, its look and its alarm requests are the real ones, on MilO's real
 * clock ([JumpingPhone]), with the real [ClockWatch] to have the alarm asked for again.
 *
 * Android's part is played by hand. The alarm service holds the newest request (`alarmHeld`):
 * one for a time of day is due once the PHONE's clock has passed it, and one that counts from
 * boot is due when its wait is over, whatever the date ([alarmIsDue]). Android's word that the
 * clock was set reaches the watch.
 */
// runCurrent() is how a test lets the reminder's coroutines run.
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MonthlyReminderClockJumpFixture : MonthlyReminderFixture() {
    protected lateinit var phone: JumpingPhone
    protected lateinit var watch: ClockWatch

    /** The phone of the test, at [start], and the reminder's clock on it. */
    protected fun phoneAt(start: String) {
        phone = JumpingPhone(at(start))
        clock = { phone.clock.now() }
        phoneClockAgrees = { phone.clock.agreesWithPhone() }
        clockOnProbation = { phone.clock.onProbation }
        elapsedNow = { phone.elapsedMs }
    }

    /** A process of MilO: the reminder, and the watch that has its alarm asked for again. */
    protected fun TestScope.process(): MonthlyReminder {
        val reminder = reminder()
        watch =
            ClockWatch(
                clock = phone.clock,
                askAgain = listOf(reminder::arm),
                eventLog = EventLogRepository(log),
                crashFileStore = crashFiles,
                scope = backgroundScope,
            )
        watch.start()
        return reminder
    }

    /** True if Android would deliver the daily alarm now. */
    protected fun alarmIsDue() = checkNotNull(alarmHeld) { "no alarm is asked for" }.isDueOn(phone)

    /** The date is set a day ahead: the alarm is overdue and arrives, with word of the change. */
    protected fun TestScope.dateSetAhead(reminder: MonthlyReminder) {
        phone.setAhead()
        assertTrue(alarmIsDue())
        reminder.onAlarm {}
        watch.onPhoneClockSet {}
        runCurrent()
    }

    /** Some seconds later automatic time puts the clock back, and Android says so. */
    protected fun TestScope.dateSetBack() {
        phone.advance(10 * SECOND_MS)
        phone.setBack()
        watch.onPhoneClockSet {}
        runCurrent()
    }

    /** September is sent; October has Business trips and is still running. */
    protected fun lastDayOfOctober() {
        sent = listOf(sentFor(september))
        trips = trips + listOf(businessTrip("2026-10-14T08:10"), businessTrip("2026-10-30T09:00"))
    }
}
