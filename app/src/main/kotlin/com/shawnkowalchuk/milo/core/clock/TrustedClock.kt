package com.shawnkowalchuk.milo.core.clock

import kotlin.math.abs

/**
 * A change of the phone's clock of up to this much is followed at once: it is what a network
 * time correction looks like. So while nobody touches the clock, MilO's time IS the phone's.
 */
const val FOLLOW_AT_ONCE_MS = 2L * 60L * 1000L

/**
 * A larger change is believed only once the phone's clock has kept it for this long, counted on
 * the clock that runs from boot. The owner sets the date a day ahead for 5 to 16 seconds to get
 * lives in a game (ADR-005); ten minutes is far beyond that, and short enough that a date he
 * really changed is followed the same evening.
 */
const val HOLD_MS = 10L * 60L * 1000L

/**
 * A change that is being held must be seen again within this time, or its ten minutes start
 * over. "Kept for ten minutes" is only known while somebody looks: the date trick was done five
 * times in one evening, five to ten minutes apart, and two looks that both fell into such
 * seconds would otherwise pass for one change that never went away.
 */
const val SEEN_AGAIN_WITHIN_MS = 2L * 60L * 1000L

/**
 * How long an anchor that was taken with nothing to check it against stays on probation
 * ([ClockAnchor.onProbationSinceMs]): until the phone's clock is found agreeing with it at a
 * reading at least this long after it was taken. Longer than any of the owner's jumps, so an
 * anchor that was taken inside one cannot pass.
 */
const val PROBATION_MS = 2L * 60L * 1000L

/** While the phone's clock is followed, the anchor is stored again at most this often. */
const val ANCHOR_SAVE_EVERY_MS = 5L * 60L * 1000L

/**
 * The two clocks count as read at one moment if the time since boot, read before and after the
 * phone's clock, moved by no more than this. See [TrustedClock] `bothClocks`.
 */
private const val READ_TOGETHER_WITHIN_MS = 1_000L

/** How often the two clocks are read before a pair that lies apart is taken all the same. */
private const val READ_TOGETHER_TRIES = 3

/** News that nobody has asked for yet is kept, but not without end. */
private const val UNHEARD_NEWS_KEPT = 16

/**
 * MilO's own clock: the time of day that every part of MilO goes by, kept right while the
 * phone's clock is being played with (ADR-005).
 *
 * It holds a [ClockAnchor], and "now" is the anchor's time plus what the clock that runs from
 * boot has counted since. At every reading that is held against the phone's clock:
 * - within [FOLLOW_AT_ONCE_MS]: the phone's clock becomes the new anchor and is the answer;
 * - further apart: the phone is not believed. The answer is MilO's own time, and only if the
 *   phone's clock keeps the same difference for [HOLD_MS], seen all the while
 *   ([SEEN_AGAIN_WITHIN_MS]), is it taken as the new anchor.
 *
 * It is a rule about how long a change has held, never about who made it: nothing here asks
 * whether the phone's time is set to automatic.
 *
 * **An anchor on probation.** A process that starts with no anchor to go by takes the phone's
 * clock as it is, and that can be in the very seconds the date is set ahead. Such an anchor is
 * on probation until the phone's clock has agreed with it at a reading [PROBATION_MS] or more
 * after it was taken. While it is, a phone's clock found far BEHIND MilO's time is taken at
 * once, as a new anchor that is itself on probation: the owner's trick goes ahead and then
 * back, so a clock that goes back says that the anchor was taken inside a jump. A phone's clock
 * that goes AHEAD is set aside as always.
 *
 * TODO(debt): a change that is meant (a date really set by hand, a phone whose clock was wrong)
 *  is followed only by a process that has watched it for ten minutes without a gap, and the
 *  reading that follows it moves MilO's time by the whole change at once. A trip that is open
 *  at that reading is hit as trips were before MilO kept a clock. See docs/FINDINGS_LOG.md.
 *
 * TODO(debt): a process that starts with no anchor while the date is set ahead goes by that
 *  date until the phone's clock comes back, and what it stamps in those seconds stays stamped.
 *  (It shows no notification by it, and is woken two minutes on: `askForDailyAlarm`.)
 *  And an anchor taken inside one jump is proven by a reading inside a later one, if nothing of
 *  MilO read the clock in between. See docs/FINDINGS_LOG.md, 2026-10-09.
 *
 * Pure Kotlin: the two clocks are functions handed in, so every case runs in a unit test. Safe
 * to read from any thread.
 *
 * @param phoneNow the phone's wall clock, milliseconds since 1970. The only thing in MilO that
 * reads it is this class.
 * @param elapsedNow milliseconds since the phone booted, time asleep included. It cannot be set.
 * @param bootCount the number of this boot, or null if the phone does not say. Without it no
 * stored anchor can be trusted, and none is stored.
 * @param stored the anchor an earlier process left, or null. It is used only if it is of this
 * boot: a process that Android starts while the date is set ahead then still has the right time.
 * After a reboot, or with no anchor, the phone's clock is taken as it is, on probation.
 * @param save stores an anchor for the next process. It is called while a reading is held up,
 * so it must hand the writing to another thread.
 */
class TrustedClock(
    private val phoneNow: () -> Long,
    private val elapsedNow: () -> Long,
    private val bootCount: Int?,
    stored: ClockAnchor?,
    private val save: (ClockAnchor) -> Unit,
) {
    /** A change of the phone's clock that is not believed yet. */
    private class Held(
        val offsetMs: Long,
        val firstSeenElapsedMs: Long,
        var sinceElapsedMs: Long,
        var lastSeenElapsedMs: Long,
    )

    /** The two clocks at one moment: time since boot, and the phone's time of day. */
    private class Moment(val elapsedMs: Long, val phoneMs: Long)

    private val lock = Any()

    // Everything below is read and written under the lock.
    private var anchor: ClockAnchor
    private var savedAtElapsedMs: Long
    private var held: Held? = null
    private var listener: ((ClockNews) -> Unit)? = null
    private val unheard = ArrayDeque<ClockNews>()

    init {
        val now = bothClocks()
        val usable = stored?.takeIf { it.bootCount == bootCount && it.isOfThePastOf(now) }
        if (usable != null) {
            anchor = usable
            savedAtElapsedMs = usable.elapsedMs
        } else {
            // Nothing to check the phone's clock against: taken as it is, on probation.
            anchor = anchorAt(now, onProbationSinceMs = now.elapsedMs)
            savedAtElapsedMs = now.elapsedMs
            store()
        }
    }

    /**
     * Whether the anchor is kept from one process to the next. False on a phone that gives no
     * boot number: every process then starts from the phone's clock as it is.
     */
    val keptAcrossStarts: Boolean = bootCount != null

    /**
     * Whether the anchor is on probation ([PROBATION_MS]). It says what the last reading left,
     * and reads no clock itself.
     */
    val onProbation: Boolean get() = synchronized(lock) { anchor.onProbationSinceMs != null }

    /** The time of day, in milliseconds since 1970. This is what MilO's `clock` is. */
    fun now(): Long = read().first

    /**
     * Whether MilO's time is the phone's at this moment. False while a change of the phone's
     * clock is being held and not believed: an alarm asked of Android for a time on MilO's
     * clock would then be judged by the phone's, and go off at once or a day late.
     */
    fun agreesWithPhone(): Boolean = read().second

    /**
     * Names who is told what the phone's clock does, and hands over what was noticed before
     * anyone asked. The listener is called on whichever thread read the clock, after the
     * reading, so it must only pass the news on.
     */
    fun tellNewsTo(listener: (ClockNews) -> Unit) {
        val waiting =
            synchronized(lock) {
                this.listener = listener
                unheard.toList().also { unheard.clear() }
            }
        waiting.forEach(listener)
    }

    private fun read(): Pair<Long, Boolean> {
        val news = mutableListOf<ClockNews>()
        val (answer, tell) =
            synchronized(lock) {
                val answer = readLocked(news)
                val tell = listener
                if (tell == null) {
                    news.forEach { if (unheard.size < UNHEARD_NEWS_KEPT) unheard.addLast(it) }
                }
                answer to tell
            }
        // Outside the lock: whoever is told may read the clock in turn.
        if (tell != null) news.forEach(tell)
        return answer
    }

    private fun readLocked(news: MutableList<ClockNews>): Pair<Long, Boolean> {
        val now = bothClocks()
        val elapsed = now.elapsedMs
        val own = anchor.wallMs + (elapsed - anchor.elapsedMs)
        val offset = now.phoneMs - own
        val was = held

        if (abs(offset) <= FOLLOW_AT_ONCE_MS) {
            if (was != null) {
                held = null
                news += ClockNews.CameBack(was.offsetMs, elapsed - was.firstSeenElapsedMs)
            }
            val since = anchor.onProbationSinceMs
            val proven = since != null && elapsed - since >= PROBATION_MS
            anchor = anchorAt(now, onProbationSinceMs = since.takeUnless { proven })
            if (proven || elapsed - savedAtElapsedMs >= ANCHOR_SAVE_EVERY_MS) store()
            return now.phoneMs to true
        }

        if (anchor.onProbationSinceMs != null && offset < 0) {
            // Far behind an anchor that nothing had confirmed: the anchor was the mistake.
            if (was != null) {
                held = null
                news += ClockNews.ChangedAgain(was.offsetMs, elapsed - was.firstSeenElapsedMs)
            }
            anchor = anchorAt(now, onProbationSinceMs = elapsed)
            store()
            news += ClockNews.StartedAhead(-offset)
            return now.phoneMs to true
        }

        if (was == null || abs(offset - was.offsetMs) > FOLLOW_AT_ONCE_MS) {
            if (was != null) {
                news += ClockNews.ChangedAgain(was.offsetMs, elapsed - was.firstSeenElapsedMs)
            }
            held = Held(offset, elapsed, elapsed, elapsed)
            news += ClockNews.SetAside(offset)
            return own to false
        }

        // The same change as at the last reading. If nobody looked for too long it may have
        // gone and come again in between, so its ten minutes start over.
        if (elapsed - was.lastSeenElapsedMs > SEEN_AGAIN_WITHIN_MS) was.sinceElapsedMs = elapsed
        was.lastSeenElapsedMs = elapsed
        if (elapsed - was.sinceElapsedMs < HOLD_MS) return own to false

        // Watched for ten minutes without a gap, which is more than probation asks.
        held = null
        anchor = anchorAt(now, onProbationSinceMs = null)
        store()
        news += ClockNews.Followed(offset, elapsed - was.sinceElapsedMs)
        return now.phoneMs to true
    }

    /**
     * The two clocks, read as one moment. They are two calls, and a process that Android stops
     * exactly between them, or a phone that goes to sleep there, would hold a fresh time of day
     * against an old time since boot: a difference that nobody made. So the time since boot is
     * read once more after the phone's clock, and a pair it moved under is read again.
     */
    private fun bothClocks(): Moment {
        repeat(READ_TOGETHER_TRIES - 1) {
            val elapsed = elapsedNow()
            val phone = phoneNow()
            if (elapsedNow() - elapsed <= READ_TOGETHER_WITHIN_MS) return Moment(elapsed, phone)
        }
        // Stopped between the two every time. The last pair is taken as it comes: a reading
        // has to answer, and the next one puts right what this one gets wrong.
        return Moment(elapsedNow(), phoneNow())
    }

    private fun anchorAt(now: Moment, onProbationSinceMs: Long?) = ClockAnchor(
        bootCount = bootCount ?: NO_BOOT_COUNT,
        wallMs = now.phoneMs,
        elapsedMs = now.elapsedMs,
        onProbationSinceMs = onProbationSinceMs,
    )

    /** False for an anchor that claims a later moment of this boot than [now]: it is not of it. */
    private fun ClockAnchor.isOfThePastOf(now: Moment): Boolean =
        elapsedMs <= now.elapsedMs && (onProbationSinceMs ?: elapsedMs) <= elapsedMs

    private fun store() {
        savedAtElapsedMs = anchor.elapsedMs
        // Without a boot number the next process could not tell this boot from another.
        if (bootCount != null) save(anchor)
    }

    private companion object {
        /** Stands in the anchor when the phone gives no boot number. Such an anchor is never stored. */
        const val NO_BOOT_COUNT = -1
    }
}
